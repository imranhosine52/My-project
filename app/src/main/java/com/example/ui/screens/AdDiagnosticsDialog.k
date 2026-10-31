package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ads.UnifiedAdBanner
import com.example.ads.UnifiedAdManager
import com.example.ads.findActivity

private val TealAccent = Color(0xFF00C9A7)
private val CyanAccent = Color(0xFF00E5FF)
private val AlertRed = Color(0xFFFF3B30)

/**
 * 🛠️ অ্যাড নেটওয়ার্ক ডায়াগনস্টিক ও লাইভ টেস্ট ডায়ালগ
 * Start.io এবং Adsterra-এর লাইভ স্ট্যাটাস ও বাটন টেস্ট
 */
@Composable
fun AdDiagnosticsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val adConfig by UnifiedAdManager.adConfigState.collectAsState()

    var statusLog by remember { mutableStateOf("Ready to test Start.io & Adsterra.") }
    var isTestingAd by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
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
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10141F)),
                border = BorderStroke(1.2.dp, Color(0xFF222B3D))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // ১. হেডার
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = TealAccent)
                            Text(
                                text = "Ad Network Diagnostics",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF8E95A5))
                        }
                    }

                    HorizontalDivider(color = Color(0xFF222B3D), thickness = 0.8.dp)

                    // ২. লাইভ কনফিগারেশন স্ট্যাটাস কার্ড
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF161C2A),
                        border = BorderStroke(0.8.dp, Color(0xFF28344A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Active Network Rules:", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("• Primary: ${adConfig.primaryNetwork.uppercase()}", color = Color.White, fontSize = 11.5.sp)
                            Text("• Fallback: ${adConfig.fallbackNetwork.uppercase()}", color = Color.White, fontSize = 11.5.sp)
                            Text("• Start.io Enabled: ${adConfig.startio?.enabled == true} (App ID: ${adConfig.startio?.appId})", color = if (adConfig.startio?.enabled == true) TealAccent else Color.Gray, fontSize = 11.5.sp)
                            Text("• Adsterra Enabled: ${adConfig.adsterra?.enabled == true}", color = if (adConfig.adsterra?.enabled == true) TealAccent else Color.Gray, fontSize = 11.5.sp)
                            Text("• Unlocked Episodes Rule: First ${adConfig.rules?.freeUnlockedEpisodes} Episode(s) Free", color = Color(0xFFCCD0DB), fontSize = 11.5.sp)
                        }
                    }

                    // ৩. রিয়েল-টাইম লগ কনসোল
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black,
                        border = BorderStroke(0.6.dp, Color(0xFF333E50)),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                    ) {
                        Box(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "LOG: $statusLog",
                                color = if (statusLog.contains("failed", true) || statusLog.contains("error", true)) AlertRed else TealAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // ৪. 🎯 টেস্ট বাটনসমূহ
                    Text("1-Click Ad Testers:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    // বাটন ১: ইন্টারস্টিশিয়াল অ্যাড টেস্ট
                    Button(
                        onClick = {
                            isTestingAd = true
                            statusLog = "Requesting Start.io Interstitial Ad..."
                            UnifiedAdManager.showInterstitial(context, isVip = false) {
                                isTestingAd = false
                                statusLog = "✓ Interstitial Ad closed / completed."
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Test Start.io Interstitial Ad", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // বাটন ২: রিওয়ার্ডেড ভিডিও অ্যাড টেস্ট
                    Button(
                        onClick = {
                            if (activity != null) {
                                isTestingAd = true
                                statusLog = "Requesting Start.io Rewarded Video Ad..."
                                UnifiedAdManager.showRewardedVideo(
                                    context = activity,
                                    isVip = false,
                                    onRewardUnlocked = {
                                        statusLog = "🎉 Reward Unlocked! User earned episode access."
                                        Toast.makeText(context, "🎉 Reward Unlocked!", Toast.LENGTH_SHORT).show()
                                    },
                                    onAdNotReadyOrFailed = { reason ->
                                        isTestingAd = false
                                        statusLog = "❌ Fallback Triggered: $reason"
                                    },
                                    onAdClosed = { earned ->
                                        isTestingAd = false
                                        statusLog = "Reward Ad Closed. Earned: $earned"
                                    }
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Text("Test Start.io Rewarded Video", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // বাটন ৩: Adsterra ডিরেক্ট স্মার্টলিংক টেস্ট
                    Button(
                        onClick = {
                            statusLog = "Opening Adsterra Direct Smartlink via Chrome Custom Tab..."
                            val ok = UnifiedAdManager.openAdsterraDirectLink(context, isVip = false)
                            statusLog = if (ok) "✓ Chrome Custom Tab opened successfully." else "❌ Failed to open Custom Tab (URL empty)."
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C5CE7)),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Test Adsterra Smartlink (Chrome Tab)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // বাটন ৪: ব্যাকগ্রাউন্ড প্রি-লোডার পুনরায় ট্রিগার করা
                    OutlinedButton(
                        onClick = {
                            statusLog = "Triggering manual preloading for Start.io..."
                            UnifiedAdManager.preloadInterstitial(context)
                            UnifiedAdManager.preloadRewardedVideo(context)
                            statusLog = "✓ Preload requests sent to Start.io."
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF28344A)),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Force Preload Start.io Ads", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    // ৫. লাইভ ব্যানার প্রিভিউ বক্স
                    Text("Live Banner Preview (Start.io):", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF141A26),
                        border = BorderStroke(0.8.dp, Color(0xFF222B3D)),
                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            UnifiedAdBanner(isVip = false)
                        }
                    }
                }
            }
        }
    }
}
