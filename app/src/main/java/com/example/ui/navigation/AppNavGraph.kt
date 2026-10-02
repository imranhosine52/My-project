package com.example.ui.navigation

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CreatorPageDto
import com.example.data.model.UserReelDto
import com.example.ui.screens.*
import com.example.ui.screens.chat.CommunityChatScreen
import com.example.ui.screens.chat.InboxScreen
import com.example.ui.screens.chat.PersonalChatScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.profile.CreatorStudioScreen
import com.example.ui.screens.profile.PublicCreatorProfileScreen
import com.example.ui.screens.profile.RegularUserProfileScreen
import com.example.ui.screens.reels.*
import com.example.ui.screens.shorts.ShortsPlayerScreen
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.BottomNavTab
import com.example.ui.viewmodel.DramaFlixViewModel
import com.example.ui.viewmodel.ReelsViewModel

/**
 * 🗺️ AppNavGraph
 * সমস্ত স্ক্রিনের কম্পোজিশন ও নেভিগেশন সুইচার
 */
@Composable
fun AppNavGraph(
    currentScreen: Screen,
    viewModel: DramaFlixViewModel,
    reelsViewModel: ReelsViewModel,
    authState: AuthUiState,
    pendingUploadMode: String,
    onNavigateTo: (Screen, BottomNavTab?) -> Unit,
    onBackClick: () -> Unit,
    openDramaDirect: (String, Boolean) -> Unit,
    onSetPendingUploadMode: (String) -> Unit,
    onLaunchVideoPicker: (String) -> Unit,
    onSwitchToCreatorStudio: (CreatorPageDto) -> Unit,
    onSwitchToPersonalProfile: () -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uploadState = reelsViewModel.uploadState.collectAsStateWithLifecycle().value

    Box(modifier = modifier.fillMaxSize()) {
        when (currentScreen) {
            // ১. হোম স্ক্রিন
            is Screen.Home -> {
                HomeScreen(
                    viewModel = viewModel,
                    initialCategory = currentScreen.category,
                    onNavigateToPlayer = { slug -> openDramaDirect(slug, false) },
                    onNavigateToVip = { onNavigateTo(Screen.Vip, null) },
                    onNavigateToSearch = { onNavigateTo(Screen.Search, null) },
                    onNavigateToNotification = { onNavigateTo(Screen.Notification, null) }
                )
            }

            // ২. শর্টস প্লেয়ার
            is Screen.ShortsPlayer -> {
                ShortsPlayerScreen(
                    slug = currentScreen.slug,
                    viewModel = viewModel,
                    onBackClick = onBackClick,
                    onNavigateToVip = { onNavigateTo(Screen.Vip, null) }
                )
            }

            // ৩. মূল ড্রামা প্লেয়ার
            is Screen.Player -> {
                PlayerScreen(
                    slug = currentScreen.slug,
                    viewModel = viewModel,
                    onBackClick = onBackClick,
                    onNavigateToVip = { onNavigateTo(Screen.Vip, null) },
                    onRelatedDramaClick = { newSlug -> openDramaDirect(newSlug, false) },
                    onNavigateToDownloads = { onNavigateTo(Screen.Downloads, BottomNavTab.DOWNLOADS) }
                )
            }

            // ৪. রিলস ফিড স্ক্রিন
            is Screen.Reels -> {
                ReelsFeedScreen(
                    viewModel = reelsViewModel,
                    isLoggedIn = authState.isLoggedIn,
                    currentUserName = authState.userProfile?.displayName ?: "User",
                    currentUserAvatar = authState.userProfile?.avatar,
                    onBackClick = onBackClick,
                    onNavigateToHome = { onNavigateTo(Screen.Home(), BottomNavTab.HOME) },
                    onNavigateToInbox = { onNavigateTo(Screen.Inbox, null) },
                    onNavigateToProfile = { onNavigateTo(Screen.Profile, BottomNavTab.ME) },
                    onOpenCreateReel = { mode ->
                        onSetPendingUploadMode(mode)
                        onLaunchVideoPicker("video/*")
                    },
                    onOpenPageProfile = { pageId ->
                        onNavigateTo(Screen.PublicCreatorProfile(pageId), null)
                    },
                    onNavigateToSearch = { initialTag ->
                        if (initialTag.startsWith("#")) {
                            onNavigateTo(Screen.HashtagDetail(initialTag), null)
                        } else {
                            onNavigateTo(Screen.ReelsSearch(initialQuery = initialTag), null)
                        }
                    },
                    onNavigateToVip = { onNavigateTo(Screen.Vip, null) },
                    onRequireLogin = onRequireLogin
                )
            }

            // ৫. পাবলিক ক্রিয়েটর পেজ
            is Screen.PublicCreatorProfile -> {
                PublicCreatorProfileScreen(
                    pageId = currentScreen.pageId,
                    reelsViewModel = reelsViewModel,
                    isLoggedIn = authState.isLoggedIn,
                    onRequireLogin = onRequireLogin,
                    onBackClick = onBackClick,
                    onReelClick = { onNavigateTo(Screen.Reels, null) },
                    onOpenDirectMessage = { creatorId, creatorName ->
                        onNavigateTo(
                            Screen.PersonalChat(
                                otherUserId = creatorId,
                                otherUserName = creatorName,
                                otherUserAvatar = null
                            ),
                            null
                        )
                    }
                )
            }

            // ৬. সাধারণ ইউজার প্রোফাইল
            is Screen.RegularUserProfile -> {
                RegularUserProfileScreen(
                    targetUserId = currentScreen.userId,
                    isLoggedIn = authState.isLoggedIn,
                    onRequireLogin = onRequireLogin,
                    onBackClick = onBackClick,
                    onReelClick = { onNavigateTo(Screen.Reels, null) },
                    onOpenDirectMessage = { uId, uName ->
                        onNavigateTo(
                            Screen.PersonalChat(
                                otherUserId = uId,
                                otherUserName = uName,
                                otherUserAvatar = null
                            ),
                            null
                        )
                    },
                    onOpenFriendProfile = { friendId ->
                        onNavigateTo(Screen.RegularUserProfile(friendId), null)
                    }
                )
            }

            // ৭. সাজেস্টেড ফ্রেন্ডস / সোশ্যাল হাব
            is Screen.SuggestedAccounts -> {
                SuggestedAccountsScreen(
                    reelsViewModel = reelsViewModel,
                    onBackClick = onBackClick,
                    onOpenProfile = { userId ->
                        onNavigateTo(Screen.RegularUserProfile(userId), null)
                    },
                    onOpenDirectMessage = { otherUserId, otherUserName ->
                        onNavigateTo(
                            Screen.PersonalChat(
                                otherUserId = otherUserId,
                                otherUserName = otherUserName,
                                otherUserAvatar = null
                            ),
                            null
                        )
                    },
                    onReelClick = { onNavigateTo(Screen.Reels, null) }
                )
            }

            // ৮. ইনবক্স
            is Screen.Inbox -> {
                InboxScreen(
                    currentUserId = authState.userProfile?.id ?: "guest",
                    currentUserAvatar = authState.userProfile?.avatar,
                    onOpenPersonalChat = { otherId, otherName, otherAvatar ->
                        onNavigateTo(
                            Screen.PersonalChat(
                                otherUserId = otherId,
                                otherUserName = otherName,
                                otherUserAvatar = otherAvatar
                            ),
                            null
                        )
                    },
                    onOpenSearch = { onNavigateTo(Screen.SuggestedAccounts, null) },
                    onCreateStoryOrReel = {
                        onSetPendingUploadMode("reel")
                        onLaunchVideoPicker("video/*")
                    }
                )
            }

            // ৯. পার্সোনাল চ্যাট
            is Screen.PersonalChat -> {
                PersonalChatScreen(
                    myUserId = authState.userProfile?.id ?: "guest",
                    myUserName = authState.userProfile?.displayName ?: "User",
                    myUserAvatar = authState.userProfile?.avatar,
                    recipientUserId = currentScreen.otherUserId,
                    recipientUserName = currentScreen.otherUserName,
                    recipientUserAvatar = currentScreen.otherUserAvatar,
                    onBackClick = onBackClick
                )
            }

            // ১০. রিলস সার্চ
            is Screen.ReelsSearch -> {
                ReelsSearchScreen(
                    initialQuery = currentScreen.initialQuery,
                    onBackClick = onBackClick,
                    onNavigateToResults = { query ->
                        onNavigateTo(Screen.ReelsSearchResult(query = query), null)
                    }
                )
            }

            // ১১. রিলস সার্চ রেজাল্ট
            is Screen.ReelsSearchResult -> {
                ReelsSearchResultScreen(
                    searchQuery = currentScreen.query,
                    viewModel = reelsViewModel,
                    onBackClick = onBackClick,
                    onSearchSubmit = { newQuery ->
                        onNavigateTo(Screen.ReelsSearchResult(query = newQuery), null)
                    },
                    onReelClick = { onNavigateTo(Screen.Reels, null) },
                    onOpenCreatorProfile = { pageId ->
                        onNavigateTo(Screen.PublicCreatorProfile(pageId), null)
                    },
                    onOpenHashtagExplorer = { tag ->
                        onNavigateTo(Screen.HashtagDetail(tag), null)
                    }
                )
            }

            // ১২. হ্যাশট্যাগ এক্সপ্লোরার
            is Screen.HashtagDetail -> {
                HashtagDetailScreen(
                    hashtag = currentScreen.hashtag,
                    onBackClick = onBackClick,
                    onReelClick = { onNavigateTo(Screen.Reels, null) }
                )
            }

            // ১৩. ভিডিও ট্রিমার
            is Screen.VideoTrimmer -> {
                VideoTrimmerScreen(
                    videoUri = currentScreen.videoUri,
                    isSeries = currentScreen.isSeries,
                    onBackClick = onBackClick,
                    onNextClick = { trimmedPath, isMuted ->
                        if (currentScreen.isSeries || pendingUploadMode == "series") {
                            onNavigateTo(
                                Screen.SeriesEpisodePublish(
                                    trimmedVideoPath = trimmedPath,
                                    isMuted = isMuted
                                ),
                                null
                            )
                        } else {
                            onNavigateTo(
                                Screen.ReelDetailsPublish(
                                    trimmedVideoPath = trimmedPath,
                                    isMuted = isMuted
                                ),
                                null
                            )
                        }
                    }
                )
            }

            // ১৪. সাধারণ রিলস পাবলিশ
            is Screen.ReelDetailsPublish -> {
                val currentUserIdInt = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                ReelDetailsPublishScreen(
                    trimmedVideoPath = currentScreen.trimmedVideoPath,
                    creatorPage = uploadState.creatorPage,
                    userId = currentUserIdInt,
                    onBackClick = onBackClick,
                    onPublishSuccessExit = {
                        onNavigateTo(Screen.Reels, BottomNavTab.REELS)
                        reelsViewModel.loadFeed(tab = "for_you")
                    }
                )
            }

            // ১৫. সিরিজ ড্রামা পর্ব পাবলিশ
            is Screen.SeriesEpisodePublish -> {
                val currentUserIdInt = authState.userProfile?.id?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                SeriesEpisodePublishScreen(
                    trimmedVideoPath = currentScreen.trimmedVideoPath,
                    creatorPage = uploadState.creatorPage,
                    userId = currentUserIdInt,
                    onBackClick = onBackClick,
                    onPublishSuccessExit = {
                        onNavigateTo(Screen.Reels, BottomNavTab.REELS)
                        reelsViewModel.loadFeed(tab = "for_you")
                    }
                )
            }

            // ১৬. ক্রিয়েটর স্টুডিও
            is Screen.CreatorStudio -> {
                CreatorStudioScreen(
                    page = currentScreen.page,
                    reelsViewModel = reelsViewModel,
                    onSwitchToPersonalProfile = onSwitchToPersonalProfile,
                    onBackClick = onBackClick,
                    onReelClick = { onNavigateTo(Screen.Reels, null) },
                    onCreateReelClick = {
                        onSetPendingUploadMode("reel")
                        onLaunchVideoPicker("video/*")
                    }
                )
            }

            // ১৭. ড্রামা সার্চ
            is Screen.Search -> {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = { slug -> openDramaDirect(slug, false) }
                )
            }

            // ১৮. ভিআইপি স্ক্রিন
            is Screen.Vip -> {
                VipScreen(
                    viewModel = viewModel,
                    onNavigateBack = onBackClick
                )
            }

            // ১৯. ওয়াচলিস্ট
            is Screen.Watchlist -> {
                WatchlistScreen(
                    viewModel = viewModel,
                    onNavigateToPlayer = { slug -> openDramaDirect(slug, false) }
                )
            }

            // ২০. ইউজার প্রোফাইল স্ক্রিন
            is Screen.Profile -> {
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateToVip = { onNavigateTo(Screen.Vip, null) },
                    onNavigateToWatchlist = { onNavigateTo(Screen.Watchlist, null) },
                    onNavigateToBrowser = { onNavigateTo(Screen.Browser(), null) },
                    onNavigateToNotification = { onNavigateTo(Screen.Notification, null) },
                    onNavigateToLocalGallery = { onNavigateTo(Screen.LocalGallery, null) },
                    onNavigateToCommunityChat = { onNavigateTo(Screen.CommunityChat, null) },
                    onSwitchToCreatorStudio = onSwitchToCreatorStudio
                )
            }

            // ২১. ইন-অ্যাপ ব্রাউজার
            is Screen.Browser -> {
                BrowserScreen(
                    initialUrl = currentScreen.initialUrl,
                    onBackClick = onBackClick
                )
            }

            // ২২. নোটিফিকেশন স্ক্রিন
            is Screen.Notification -> {
                NotificationScreen(
                    viewModel = viewModel,
                    onBackClick = onBackClick,
                    onDramaClick = { dramaSlug -> openDramaDirect(dramaSlug, false) }
                )
            }

            // ২৩. লোকাল মিডিয়া গ্যালারি
            is Screen.LocalGallery -> {
                LocalGalleryScreen(
                    onBackClick = onBackClick,
                    onVideoClick = { video -> onNavigateTo(Screen.LocalPlayer(video), null) }
                )
            }

            // ২৪. লোকাল প্লেয়ার
            is Screen.LocalPlayer -> {
                LocalPlayerScreen(
                    videoItem = currentScreen.videoItem,
                    onBackClick = onBackClick
                )
            }

            // ২৫. ডাউনলোডস স্ক্রিন
            is Screen.Downloads -> {
                DownloadsScreen(
                    onBackClick = onBackClick,
                    onPlayDownloadedVideo = { localVideoItem ->
                        onNavigateTo(Screen.LocalPlayer(localVideoItem), null)
                    }
                )
            }

            // ২৬. কমিউনিটি চ্যাট
            is Screen.CommunityChat -> {
                CommunityChatScreen(
                    viewModel = viewModel,
                    onBackClick = onBackClick
                )
            }
        }
    }
}
