@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.InvoiceItemDto
import com.example.data.model.UserProfileDto
import com.example.ui.VipCrownVectorIcon
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DramaFlixViewModel

private val ActionGreen = Color(0xFF00D166)
private val BannerGreen = Color(0xFF06331E)
private val BannerTextGreen = Color(0xFF00E676)

@Composable
fun ProfileScreen(
    viewModel: DramaFlixViewModel,
    onNavigateToVip: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToNotification: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authState by viewModel.authUiState.collectAsStateWithLifecycle()
    val vipState by viewModel.vipUiState.collectAsStateWithLifecycle()
    val watchlistState by viewModel.watchlistUiState.collectAsStateWithLifecycle()

    var showAuthDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showInvoiceSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshVipStatusAndProfile()
    }

    val guestId = remember { "535" + (100000..999999).random() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Header: User Profile or Guest Login
            if (authState.isLoggedIn && authState.userProfile != null) {
                val user = authState.userProfile!!
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clickable { showEditProfileDialog = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(TealAccent, ActionGreen))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!user.avatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(user.avatar)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = user.displayName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = user.displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(ActionGreen)
                                .align(Alignment.BottomEnd),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = user.displayName,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (vipState.isVip) {
                                VipCrownVectorIcon(modifier = Modifier.size(18.dp, 14.dp))
                            }
                        }

                        val uid = user.effectiveAccountId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("UID", uid))
                                    Toast.makeText(context, "UID copied!", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text("ID: $uid", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    if (vipState.isVip) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldVip.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, GoldVip)
                        ) {
                            Text(
                                text = "VIP",
                                color = GoldVip,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Log in to your account",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ID: $guestId",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .background(SurfaceDark, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Button(
                        onClick = { showAuthDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Log in", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Official Website Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BannerGreen)
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://playdramaflix.com")))
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = BannerTextGreen, modifier = Modifier.size(16.dp))
                Text(
                    text = "Official website: https://playdramaflix.com",
                    color = BannerTextGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Premium & Tasks Group
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.Star,
                        title = "Get Premium",
                        subtitle = "No ads • 1080P quality • Multi-downloads",
                        iconTint = GoldVip,
                        onClick = onNavigateToVip
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.PlayArrow,
                        title = "Tasks for Free Premium",
                        subtitle = "Unlock 2 hours full VIP access",
                        iconTint = Color(0xFFFFA726),
                        onClick = onNavigateToVip
                    )
                }
            }

            // Library & Messages Group
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.List,
                        title = "My List",
                        badge = watchlistState.savedDramas.size.toString(),
                        onClick = onNavigateToWatchlist
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Notifications,
                        title = "Messages",
                        badge = "3",
                        badgeColor = Color.Red,
                        onClick = onNavigateToNotification
                    )
                }
            }

            // Community & Social Group
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Default.Send,
                        title = "Community",
                        subtitle = "Join our Telegram community",
                        badge = "Telegram",
                        badgeColor = Color(0xFF29B6F6),
                        onClick = {
                            try {
                                val telegramIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/playdramaflix"))
                                context.startActivity(telegramIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Telegram: t.me/playdramaflix", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Add,
                        title = "Posts",
                        badge = "Coming Soon",
                        onClick = {
                            Toast.makeText(context, "Community Posts feature is coming soon!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // Account & Settings Group
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column {
                    if (authState.isLoggedIn) {
                        ProfileMenuRow(
                            icon = Icons.Default.Edit,
                            title = "Edit Profile",
                            subtitle = "Customize your name & avatar",
                            iconTint = ActionGreen,
                            onClick = { showEditProfileDialog = true }
                        )
                        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    }

                    ProfileMenuRow(
                        icon = Icons.Default.Check,
                        title = "Payment & Invoices",
                        subtitle = "View VIP transaction history",
                        onClick = { showInvoiceSheet = true }
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Share,
                        title = "In-App Web Browser",
                        subtitle = "Fast mobile web browsing",
                        onClick = onNavigateToBrowser
                    )
                    HorizontalDivider(color = BorderDark, thickness = 0.5.dp)
                    ProfileMenuRow(
                        icon = Icons.Default.Settings,
                        title = "Settings & Updates",
                        badge = "New Version",
                        badgeColor = BannerTextGreen,
                        onClick = { viewModel.checkAppVersion() }
                    )
                }
            }

            // Sign Out Option
            if (authState.isLoggedIn) {
                OutlinedButton(
                    onClick = { viewModel.signOut(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, RedAccent.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedAccent)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = RedAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                }
            }

            // App Version Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "PlayDramaFlix v2.1.0 • Built with ❤️ for Asian Drama Fans",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }

        // Auth Dialog
        if (showAuthDialog) {
            AuthBottomSheetDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }

        // Edit Profile Dialog
        if (showEditProfileDialog && authState.userProfile != null) {
            EditProfileDialog(
                currentUser = authState.userProfile!!,
                onSave = { newName, newAvatar ->
                    viewModel.signInOrRegisterWithGoogleEmail(
                        email = authState.userProfile?.email ?: "user@playdramaflix.com",
                        name = newName,
                        avatar = newAvatar
                    ) { success ->
                        if (success) {
                            Toast.makeText(context, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            showEditProfileDialog = false
                        }
                    }
                },
                onDismiss = { showEditProfileDialog = false }
            )
        }

        // Invoices Sheet (Fixed Zero-Error List)
        if (showInvoiceSheet) {
            InvoiceHistorySheet(
                invoices = vipState.invoiceHistory,
                onDismiss = { showInvoiceSheet = false }
            )
        }
    }
}

@Composable
private fun EditProfileDialog(
    currentUser: UserProfileDto,
    onSave: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(currentUser.displayName) }
    var selectedAvatarUri by remember { mutableStateOf(currentUser.avatar) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedAvatarUri = uri.toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, BorderDark),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit Profile",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(SurfaceVariantDark)
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!selectedAvatarUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(selectedAvatarUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = "Tap photo to change from gallery",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                OutlinedTextField(
                    value = inputName,
                    onValueChange = { inputName = it },
                    label = { Text("Display Name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ActionGreen,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BorderDark)
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }

                    Button(
                        onClick = {
                            if (inputName.isBlank()) {
                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onSave(inputName.trim(), selectedAvatarUri)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    badgeColor: Color = TextSecondary,
    iconTint: Color = TextSecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Column {
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (badge != null) {
                Text(text = badge, color = badgeColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvoiceHistorySheet(
    invoices: List<InvoiceItemDto>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Payment & Invoices", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            if (invoices.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No payment submissions found yet.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(invoices.size) { index ->
                        val inv = invoices[index]
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(inv.planName, color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                    Text(inv.displayAmount, color = GoldVip, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Method: ${inv.paymentMethod} • TrxID: ${inv.trxId}", color = TextSecondary, fontSize = 11.sp)
                                Text("Status: ${inv.status.uppercase()} • Date: ${inv.displayDate}", color = if (inv.status == "active" || inv.status == "approved") ActionGreen else TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
