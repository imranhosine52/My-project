@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ui.screens.profile.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.WelcomeNotificationHelper
import java.io.File
import java.util.Locale

private val ActionGreen = Color(0xFF00E676)
private val TelegramBlue = Color(0xFF2AABEE)
private val GoldVip = Color(0xFFFFB300)

/**
 * ⚙️ সেটিংস, পুশ নোটিফিকেশন ও ক্যাশ ক্লিয়ার বটম শীট
 */
@Composable
fun ProfileSettingsSheet(
    installedVersion: String,
    onStartUpdateScan: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val notifPrefs = remember { context.getSharedPreferences("drama_notif_prefs", Context.MODE_PRIVATE) }
    var notificationsEnabled by remember {
        mutableStateOf(notifPrefs.getBoolean("push_notifications_enabled", true))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10141F),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ড্র্যাগ ইন্ডিকেটর বার
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFF333D4F),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            // হেডার রো
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settings & Preferences",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMutedSlate
                    )
                }
            }

            // ১. অ্যাপ ভার্সন ও আপডেট স্ক্যানার কার্ড
            ModernMenuGroupCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = ActionGreen
                        )
                        Column {
                            Text(
                                text = "App Version & Update",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = "Current Installed: v$installedVersion",
                                color = TextMutedSlate,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Button(
                        onClick = onStartUpdateScan,
                        colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Check & Scan for Updates",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }

            // ২. পুশ নোটিফিকেশন কন্ট্রোল
            ModernMenuGroupCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = GoldVip
                        )
                        Column {
                            Text(
                                text = "Push Notifications",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Alerts on new drama episodes & releases",
                                color = TextMutedSlate,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { isEnabled ->
                            notificationsEnabled = isEnabled
                            notifPrefs.edit().putBoolean("push_notifications_enabled", isEnabled).apply()
                            if (isEnabled) {
                                WelcomeNotificationHelper.sendWelcomeNotification(context, force = true)
                                Toast.makeText(context, "Notifications Enabled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Notifications Disabled", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ActionGreen,
                            uncheckedThumbColor = TextMutedSlate,
                            uncheckedTrackColor = Color(0xFF1E2838)
                        )
                    )
                }
            }

            // ৩. পাসওয়ার্ড ও রিয়েল ক্যাশ ডিলিট সেকশন
            ModernMenuGroupCard {
                ModernMenuRowItem(
                    icon = Icons.Default.Lock,
                    title = "Change Password",
                    subtitle = "Update your account security password",
                    iconTint = TelegramBlue,
                    onClick = onOpenChangePassword
                )

                HorizontalDivider(color = CardBorderStroke, thickness = 0.8.dp)

                // 🎯 আসল ক্যাশ ক্লিয়ার ইঞ্জিন (রিয়েল মেমোরি রিলিজ)
                ModernMenuRowItem(
                    icon = Icons.Default.CleaningServices,
                    title = "Clear Cache & Media",
                    subtitle = "Free up device storage & temporary buffers",
                    iconTint = Color(0xFFFF7043),
                    onClick = {
                        val freedBytes = performRealCacheCleanup(context)
                        val freedMb = freedBytes / (1024.0 * 1024.0)
                        val message = if (freedMb > 0.05) {
                            String.format(Locale.US, "✓ Cleared %.1f MB of cache memory!", freedMb)
                        } else {
                            "✓ App cache is already clean!"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * 🧹 ফোনের ক্যাশ ডিরেক্টরি থেকে সমস্ত টেম্প ফাইল ডিলিট করার আসল হেল্পার
 */
private fun performRealCacheCleanup(context: Context): Long {
    var totalDeletedBytes = 0L
    try {
        context.cacheDir?.let { cacheDir ->
            totalDeletedBytes += calculateFolderSize(cacheDir)
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        context.externalCacheDir?.let { extCache ->
            totalDeletedBytes += calculateFolderSize(extCache)
            extCache.deleteRecursively()
            extCache.mkdirs()
        }
    } catch (_: Exception) {}
    return totalDeletedBytes
}

private fun calculateFolderSize(dir: File): Long {
    var size = 0L
    val files = dir.listFiles() ?: return 0L
    for (file in files) {
        size += if (file.isDirectory) calculateFolderSize(file) else file.length()
    }
    return size
}
