package com.example.ui.navigation // 👈 প্যাকেজ ui/navigation করা হয়েছে

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ads.UnifiedAdManager
import com.example.ui.components.AuthBottomSheetDialog
import com.example.ui.components.InAppBrowserDialog
import com.example.ui.components.SocialBarAdOverlay
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.chat.components.FloatingCommunityChatWidget
import com.example.ui.screens.profile.PageApplicationDialog
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.ui.viewmodel.ReelsViewModel

/**
 * 🌐 অ্যাপের ভাসমান উইজেট, অ্যাড ওভারলে এবং সমস্ত গ্লোবাল ডায়ালগ হ্যান্ডলার
 */
@Composable
fun BoxScope.GlobalOverlays(
    currentScreen: Screen,
    isLandscape: Boolean,
    isVip: Boolean,
    userProfileId: String?,
    userDisplayName: String,
    userEmail: String?,
    userAvatar: String?,
    showAuthDialog: Boolean,
    showPageApplyDialog: Boolean,
    updateStateShowDialog: Boolean,
    updateInfo: com.example.data.model.AppVersionCheckResponse?,
    inAppBrowserRequest: com.example.ads.InAppBrowserRequest?,
    viewModel: DramaFlixViewModel,
    reelsViewModel: ReelsViewModel,
    onNavigateToCommunityChat: () -> Unit,
    onDismissAuthDialog: () -> Unit,
    onDismissPageApplyDialog: () -> Unit,
    onPageApplySuccess: () -> Unit,
    onDismissUpdateDialog: () -> Unit
) {
    // কোন কোন স্ক্রিনে ফ্লোটিং চ্যাট বন্ধ থাকবে
    val shouldHideFloatingChat = currentScreen is Screen.Player ||
            currentScreen is Screen.ShortsPlayer ||
            currentScreen is Screen.Reels ||
            currentScreen is Screen.ReelsSearch ||
            currentScreen is Screen.ReelsSearchResult ||
            currentScreen is Screen.HashtagDetail ||
            currentScreen is Screen.VideoTrimmer ||
            currentScreen is Screen.ReelDetailsPublish ||
            currentScreen is Screen.SeriesEpisodePublish ||
            currentScreen is Screen.CreatorStudio ||
            currentScreen is Screen.PublicCreatorProfile ||
            currentScreen is Screen.RegularUserProfile ||
            currentScreen is Screen.SuggestedAccounts ||
            currentScreen is Screen.PersonalChat ||
            currentScreen is Screen.Inbox ||
            currentScreen is Screen.CommunityChat

    val isBottomNavHidden = shouldHideBottomNav(currentScreen, isLandscape)

    // =========================================================================
    // 💬 ১. ফ্লোটিং চ্যাট উইজেট
    // =========================================================================
    if (!shouldHideFloatingChat) {
        FloatingCommunityChatWidget(
            currentUserId = userProfileId ?: "guest",
            currentUserName = userDisplayName,
            currentUserEmail = userEmail,
            currentUserAvatar = userAvatar,
            isVip = isVip,
            onOpenFullScreenChat = onNavigateToCommunityChat,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 46.dp, end = 12.dp)
        )
    }

    // =========================================================================
    // 📢 ২. সোশ্যাল বার অ্যাড ওভারলে
    // =========================================================================
    if (!isBottomNavHidden &&
        currentScreen !is Screen.Player &&
        currentScreen !is Screen.Reels &&
        currentScreen !is Screen.PublicCreatorProfile &&
        currentScreen !is Screen.RegularUserProfile
    ) {
        SocialBarAdOverlay(
            isVip = isVip,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
        )
    }

    // =========================================================================
    // 🔐 ৩. গ্লোবাল অথেন্টিকেশন / লগইন ডায়ালগ
    // =========================================================================
    if (showAuthDialog) {
        AuthBottomSheetDialog(
            viewModel = viewModel,
            onDismiss = onDismissAuthDialog
        )
    }

    // =========================================================================
    // 📄 ৪. পেজ অ্যাপ্লিকেশান ডায়ালগ
    // =========================================================================
    if (showPageApplyDialog) {
        PageApplicationDialog(
            viewModel = viewModel,
            onDismiss = onDismissPageApplyDialog,
            onSuccess = onPageApplySuccess
        )
    }

    // =========================================================================
    // 🚀 ৫. অ্যাপ ফোর্স আপডেট ডায়ালগ
    // =========================================================================
    if (updateStateShowDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = onDismissUpdateDialog
        )
    }

    // =========================================================================
    // 🌐 ৬. ইন-অ্যাপ ওয়েব ব্রাউজার ডায়ালগ
    // =========================================================================
    inAppBrowserRequest?.let { req ->
        InAppBrowserDialog(
            url = req.url,
            title = req.title,
            verificationSeconds = req.verificationSeconds,
            onVerificationComplete = req.onVerified,
            onDismiss = { UnifiedAdManager.closeInAppBrowser() }
        )
    }
}
