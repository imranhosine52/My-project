package com.example.ui.navigation

import android.net.Uri
import com.example.data.model.CreatorPageDto
import com.example.data.model.LocalVideoItem
import com.example.ui.viewmodel.BottomNavTab

/**
 * 📺 শর্ট টিভি সাব-ট্যাব ন্যাভিগেশন হেলপার
 */
object ShortTvNavHelper {
    var activeSubTab: String? = null
}

/**
 * 🗺️ অ্যাপের সমস্ত স্ক্রিন রুট ডেফিনিশন (Sealed Class Screen)
 */
sealed class Screen {
    data class Home(val category: String = "Home") : Screen()
    data class Player(val slug: String) : Screen()
    data class ShortsPlayer(
        val slug: String,
        val sourceSubTab: String? = null
    ) : Screen()
    object Search : Screen()
    object Vip : Screen()
    object Watchlist : Screen()
    object Profile : Screen()
    data class Browser(val initialUrl: String? = null) : Screen()
    object Notification : Screen()
    object LocalGallery : Screen()
    data class LocalPlayer(val videoItem: LocalVideoItem) : Screen()
    object Downloads : Screen()
    object CommunityChat : Screen()
    object Inbox : Screen()
    data class PersonalChat(
        val otherUserId: String,
        val otherUserName: String,
        val otherUserAvatar: String?
    ) : Screen()
    object Reels : Screen()
    data class ReelsSearch(val initialQuery: String = "") : Screen()
    data class ReelsSearchResult(val query: String) : Screen()
    data class HashtagDetail(val hashtag: String) : Screen()
    data class VideoTrimmer(val videoUri: Uri, val isSeries: Boolean = false) : Screen()
    data class ReelDetailsPublish(val trimmedVideoPath: String, val isMuted: Boolean) : Screen()
    data class SeriesEpisodePublish(val trimmedVideoPath: String, val isMuted: Boolean) : Screen()
    data class CreatorStudio(val page: CreatorPageDto) : Screen()
    data class PublicCreatorProfile(val pageId: Int) : Screen()
    data class RegularUserProfile(val userId: Int) : Screen()
    object SuggestedAccounts : Screen()
}

/**
 * 🎯 নির্দিষ্ট স্ক্রিনের জন্য কোন বটম ট্যাব সিলেক্ট থাকবে তা নির্ধারণকারী
 */
fun resolveTabForScreen(screen: Screen): BottomNavTab {
    return when (screen) {
        is Screen.Home -> BottomNavTab.HOME
        is Screen.ShortsPlayer -> BottomNavTab.SHORT_TV
        is Screen.Reels, is Screen.ReelsSearch, is Screen.ReelsSearchResult,
        is Screen.HashtagDetail, is Screen.VideoTrimmer, is Screen.ReelDetailsPublish,
        is Screen.SeriesEpisodePublish, is Screen.PublicCreatorProfile,
        is Screen.RegularUserProfile -> BottomNavTab.REELS
        is Screen.Downloads -> BottomNavTab.DOWNLOADS
        is Screen.Profile, is Screen.CreatorStudio -> BottomNavTab.ME
        else -> BottomNavTab.HOME
    }
}

/**
 * 🔒 কোন কোন স্ক্রিনে বটম ন্যাভিগেশন বার লুকিয়ে রাখতে হবে
 */
fun shouldHideBottomNav(screen: Screen, isLandscape: Boolean): Boolean {
    return (screen is Screen.Player && isLandscape) ||
            screen is Screen.ShortsPlayer ||
            screen is Screen.Browser ||
            screen is Screen.Notification ||
            screen is Screen.LocalGallery ||
            screen is Screen.LocalPlayer ||
            screen is Screen.Search ||
            screen is Screen.CommunityChat ||
            screen is Screen.PersonalChat ||
            screen is Screen.Vip ||
            screen is Screen.VideoTrimmer ||
            screen is Screen.ReelDetailsPublish ||
            screen is Screen.SeriesEpisodePublish ||
            screen is Screen.ReelsSearch ||
            screen is Screen.ReelsSearchResult ||
            screen is Screen.HashtagDetail ||
            screen is Screen.PublicCreatorProfile ||
            screen is Screen.RegularUserProfile ||
            screen is Screen.SuggestedAccounts ||
            screen is Screen.Reels
}

/**
 * 📊 স্ক্রিন ট্র্যাকিং অ্যানালিটিক্স লেবেল
 */
fun getScreenAnalyticsLabel(screen: Screen): String? {
    return when (screen) {
        is Screen.Home -> null
        is Screen.Player -> null
        is Screen.ShortsPlayer -> null
        is Screen.Reels -> "Reels Feed Screen"
        is Screen.ReelsSearch -> "Reels Search Landing Page"
        is Screen.ReelsSearchResult -> "Reels Search Results: ${screen.query}"
        is Screen.HashtagDetail -> "Hashtag Detail: ${screen.hashtag}"
        is Screen.VideoTrimmer -> "Video Trimmer Screen"
        is Screen.ReelDetailsPublish -> "Reel Publishing Studio"
        is Screen.SeriesEpisodePublish -> "Series Episode Publishing Studio"
        is Screen.CreatorStudio -> "Creator Studio Screen"
        is Screen.PublicCreatorProfile -> "Public Creator Profile: ${screen.pageId}"
        is Screen.RegularUserProfile -> "Regular User Profile: ${screen.userId}"
        is Screen.SuggestedAccounts -> "Suggested Accounts (Find Friends)"
        is Screen.Inbox -> "Inbox Screen"
        is Screen.PersonalChat -> "Personal DM Chat"
        is Screen.Vip -> "VIP Pricing Screen"
        is Screen.Watchlist -> "My Watchlist Screen"
        is Screen.Profile -> "Profile Screen"
        is Screen.Downloads -> "Downloads Screen"
        is Screen.Search -> "Search Screen"
        is Screen.Notification -> "Notifications Screen"
        is Screen.CommunityChat -> "Community Live Chat"
        is Screen.Browser -> "In-App Browser"
        is Screen.LocalGallery -> "Local Media Gallery"
        is Screen.LocalPlayer -> "Playing Local: ${screen.videoItem.title}"
    }
}
