package com.example.ui.screens.profile.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.model.UserProfileDto
import com.example.ui.VipCrown3DIcon

private val ActionGreen = Color(0xFF00E676)
private val GoldVip = Color(0xFFFFB300)
private val TelegramBlue = Color(0xFF2AABEE)

@Composable
fun ProfileHeaderCard(
    isLoggedIn: Boolean,
    userProfile: UserProfileDto?,
    isVip: Boolean,
    vipDaysLeft: Int,
    isUploadingAvatar: Boolean,
    currentAvatarUrlOverride: String? = null,
    onAvatarClick: () -> Unit,
    onEditClick: () -> Unit,
    onLogInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val authPrefs = remember { context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkCardBackground,
        border = BorderStroke(1.dp, CardBorderStroke),
        modifier = modifier.fillMaxWidth()
    ) {
        if (isLoggedIn && userProfile != null) {
            val displayName = remember(userProfile.displayName, userProfile.name) {
                userProfile.displayName.takeIf { it.isNotBlank() && !it.equals("DramaFlix User", ignoreCase = true) }
                    ?: userProfile.name?.takeIf { it.isNotBlank() }
                    ?: "PlayDramaFlix Member"
            }

            val savedLocalAvatar = authPrefs.getString("user_avatar", null)
            val avatarUrl = currentAvatarUrlOverride
                ?: savedLocalAvatar?.takeIf { it.isNotBlank() }
                ?: userProfile.avatar?.takeIf { it.isNotBlank() }
                ?: userProfile.effectiveAvatar

            // 🎯 কভার ফটো ছাড়া সম্পূর্ণ স্লিম ও মার্জিত প্রোফাইল রো
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // প্রোফাইল অবতার বক্স
                    Box(modifier = Modifier.size(68.dp)) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2838))
                                .border(
                                    width = if (isVip) 1.8.dp else 1.dp,
                                    color = if (isVip) GoldVip else Color(0xFF2E384D),
                                    shape = CircleShape
                                )
                                .clickable { onAvatarClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(avatarUrl)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .diskCachePolicy(CachePolicy.DISABLED)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = displayName,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = displayName.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isUploadingAvatar) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.55f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = ActionGreen,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // ক্যামেরা চেঞ্জ ব্যাজ
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(ActionGreen)
                                .border(2.dp, DarkCardBackground, CircleShape)
                                .align(Alignment.BottomEnd)
                                .clickable { onAvatarClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Change Avatar",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // ইউজার তথ্য ও ভিআইপি লাইভ কাউন্টডাউন
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isVip) {
                                VipCrown3DIcon(modifier = Modifier.size(19.dp, 14.dp))
                            }
                        }

                        // একাউন্ট আইডি ও কপি চিপ
                        val uid = userProfile.effectiveAccountId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF182030))
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("UID", uid))
                                    Toast.makeText(context, "ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("ID: $uid", color = TextMutedSlate, fontSize = 11.sp)
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextMutedSlate, modifier = Modifier.size(10.dp))
                        }

                        // 🎯 ভিআইপি বাকি দিনের কাউন্টডাউন বনাম ফ্রি মেম্বার স্ট্যাটাস
                        if (isVip) {
                            val planTitle = userProfile.planName?.takeIf { it.isNotBlank() } ?: "VIP Pass"
                            val remainingText = if (vipDaysLeft > 0) "$vipDaysLeft Days Remaining" else "Active"

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF2A2000),
                                border = BorderStroke(0.6.dp, GoldVip.copy(alpha = 0.7f)),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = GoldVip,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "$planTitle • $remainingText",
                                        color = GoldVip,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Free Member",
                                color = Color(0xFF8E95A5),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // প্রোফাইল এডিট বাটন
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF192334))
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Profile",
                        tint = TelegramBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // গেস্ট স্টেট
            val guestPrefs = context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)
            val guestId = guestPrefs.getString("account_id", null) ?: "85000100"

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Log in to your account", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("Guest ID: #$guestId", color = TextMutedSlate, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Button(
                    onClick = onLogInClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ActionGreen),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Log In", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
