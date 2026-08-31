package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ads.UnifiedAdManager
import com.example.data.manager.EpisodeUnlockManager
import com.example.data.model.*
import com.example.data.repository.PlayDramaFlixRepository
import com.example.util.GoogleAuthManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class BottomNavTab(val label: String) {
    HOME("Home"),
    SEARCH("Search"),
    VIP("VIP"),
    WATCHLIST("Watchlist"),
    PROFILE("Profile")
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userProfile: UserProfileDto? = null,
    val isVip: Boolean = false,
    val authMessage: String? = null,
    val errorMessage: String? = null,
    val showAuthDialog: Boolean = false
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val categories: List<String> = listOf("Home", "Recently Added", "Popular Series", "Shorts Drama", "Drama Series", "Bangla Dub", "Hindi Dub"),
    val spotlightDramas: List<ContentItemDto> = emptyList(),
    val recentlyAdded: List<ContentItemDto> = emptyList(),
    val banglaDubbed: List<ContentItemDto> = emptyList(),
    val hindiDubbed: List<ContentItemDto> = emptyList(),
    val trendingDramas: List<ContentItemDto> = emptyList(),
    val popularDramas: List<ContentItemDto> = emptyList(),
    val dramaSeriesContent: List<ContentItemDto> = emptyList(),
    val koreanDramas: List<ContentItemDto> = emptyList(),
    val chineseDramas: List<ContentItemDto> = emptyList(),
    val animeContent: List<ContentItemDto> = emptyList(),
    val shortsContent: List<ContentItemDto> = emptyList(),
    val movieContent: List<ContentItemDto> = emptyList(),
    val vipPlans: List<SubscriptionPlanDto> = emptyList()
)

data class PlayerUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val content: ContentItemDto? = null,
    val episodes: List<EpisodeDto> = emptyList(),
    val currentEpisode: EpisodeDto? = null,
    val servers: List<ServerDto> = emptyList(),
    val selectedServer: ServerDto? = null,
    val isInWatchlist: Boolean = false,
    val isLiked: Boolean = false,
    val likesCount: Int = 0,
    val viewsCount: Long = 0L,
    val comments: List<DramaApiComment> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isPostingComment: Boolean = false,
    val recommendations: List<ContentItemDto> = emptyList(),
    val isVip: Boolean = false,
    val freeEpisodesCount: Int = 1,
    val showVipUpgradeModal: Boolean = false,
    val showEpisodeUnlockModal: Boolean = false,
    val lockedEpisodeTarget: EpisodeDto? = null,
    val unlockedEpisodeNumbers: Set<Int> = emptySet()
)

data class VipUiState(
    val isLoading: Boolean = false,
    val isVip: Boolean = false,
    val planName: String? = null,
    val expiresAt: String? = null,
    val daysRemaining: Int = 0,
    val plans: List<SubscriptionPlanDto> = emptyList(),
    val paymentGateways: List<GatewayItemDto> = emptyList(),
    val invoiceHistory: List<InvoiceItemDto> = emptyList(),
    val isSubmitting: Boolean = false,
    val submissionSuccess: Boolean = false,
    val submissionMessage: String? = null,
    val lastSubmittedInvoiceId: String? = null,
    val selectedPlan: SubscriptionPlanDto? = null,
    val userProfile: UserProfileDto? = null,
    val errorMessage: String? = null
)

data class SearchUiState(
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val selectedFilterTag: String? = null,
    val searchResults: List<ContentItemDto> = emptyList(),
    val allDramas: List<ContentItemDto> = emptyList()
)

data class WatchlistUiState(
    val savedDramas: List<ContentItemDto> = emptyList(),
    val isLoading: Boolean = false
)

data class UpdateUiState(
    val showDialog: Boolean = false,
    val updateInfo: AppVersionCheckResponse? = null
)

data class NotificationUiState(
    val isLoading: Boolean = false,
    val notifications: List<NotificationItemDto> = emptyList(),
    val readNotificationIds: Set<String> = emptySet(),
    val errorMessage: String? = null
) {
    val unreadCount: Int
        get() = notifications.count { it.id !in readNotificationIds && !it.isRead }
}

data class ActivityUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val activityData: UserActivityResponse? = null,
    val errorMessage: String? = null
)

class DramaFlixViewModel(
    private val repository: PlayDramaFlixRepository
) : ViewModel() {

    private val _homeUiState = MutableStateFlow(HomeUiState(isLoading = true))
    val homeUiState: StateFlow<HomeUiState> = _homeUiState.asStateFlow()

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()

    private val _vipUiState = MutableStateFlow(VipUiState(isLoading = true))
    val vipUiState: StateFlow<VipUiState> = _vipUiState.asStateFlow()

    private val _searchUiState = MutableStateFlow(SearchUiState())
    val searchUiState: StateFlow<SearchUiState> = _searchUiState.asStateFlow()

    private val _watchlistUiState = MutableStateFlow(WatchlistUiState())
    val watchlistUiState: StateFlow<WatchlistUiState> = _watchlistUiState.asStateFlow()

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private val _notificationUiState = MutableStateFlow(NotificationUiState(isLoading = true))
    val notificationUiState: StateFlow<NotificationUiState> = _notificationUiState.asStateFlow()

    private val _activityUiState = MutableStateFlow(ActivityUiState())
    val activityUiState: StateFlow<ActivityUiState> = _activityUiState.asStateFlow()

    private val _authUiState = MutableStateFlow(
        AuthUiState(
            isLoggedIn = repository.isUserLoggedIn(),
            userProfile = repository.getSavedUserProfile(),
            isVip = repository.isUserVip()
        )
    )
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    init {
        loadHomeContent()
        loadVipSubscriptionPlans()
        refreshVipStatusAndProfile()
        refreshAuthState()
        observeWatchlist()
        checkAppVersion()
        loadRemoteAdsConfig()
        loadNotifications()
        loadUserActivity()
    }

    fun loadUserActivity(isRefresh: Boolean = false) {
        val userId = repository.getSavedUserId().takeIf { it.isNotBlank() }
            ?: _authUiState.value.userProfile?.id
            ?: "5"

        viewModelScope.launch {
            if (isRefresh) {
                _activityUiState.update { it.copy(isRefreshing = true) }
            } else {
                _activityUiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            val result = repository.getUserActivity(userId)
            if (result.isSuccess) {
                _activityUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        activityData = result.getOrNull(),
                        errorMessage = null
                    )
                }
            } else {
                _activityUiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.exceptionOrNull()?.message
                    )
                }
            }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _notificationUiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.getNotifications()
            val list = result.getOrDefault(emptyList())
            _notificationUiState.update {
                it.copy(
                    isLoading = false,
                    notifications = list
                )
            }
        }
    }

    fun markNotificationAsRead(id: String) {
        _notificationUiState.update {
            it.copy(readNotificationIds = it.readNotificationIds + id)
        }
    }

    fun deleteNotification(id: String) {
        _notificationUiState.update { current ->
            current.copy(notifications = current.notifications.filter { it.id != id })
        }
    }

    fun clearAllNotifications() {
        _notificationUiState.update { it.copy(notifications = emptyList()) }
    }

    fun loadRemoteAdsConfig(context: Context? = null) {
        viewModelScope.launch {
            try {
                val isVip = repository.isUserVip() || _vipUiState.value.isVip
                val result = repository.fetchRemoteAdsConfig()
                val config = result.getOrDefault(repository.getCachedAdsConfig())
                context?.let { ctx ->
                    UnifiedAdManager.applyRemoteConfig(ctx, config, isVip)
                }
                val freeCount = config.rules?.freeUnlockedEpisodes ?: 1
                _playerUiState.update { it.copy(freeEpisodesCount = freeCount) }
            } catch (e: Exception) {
                Log.w("DramaFlixViewModel", "Remote ads config sync note: ${e.message}")
            }
        }
    }

    fun refreshVipStatusAndProfile() {
        viewModelScope.launch {
            val userId = repository.getSavedUserId()
            val statusResult = repository.getSubscriptionStatus(userId)
            val profileResult = if (userId.isNotBlank()) repository.getUserProfile(userId) else null

            val status = statusResult.getOrNull()
            val userProfile = profileResult?.getOrNull()?.user ?: repository.getSavedUserProfile()
            val isVip = status?.isVip == true || repository.isUserVip() || userProfile?.isVip == true
            val planName = status?.planName ?: userProfile?.planName ?: if (isVip) "VIP Pass" else null
            val expiresAt = status?.expiresAt ?: userProfile?.effectiveExpiry
            val daysRemaining = status?.daysRemaining ?: userProfile?.effectiveDaysLeft ?: if (isVip) 30 else 0
            val invoices = status?.allInvoices ?: emptyList()

            _vipUiState.update { current ->
                current.copy(
                    isVip = isVip,
                    planName = planName,
                    expiresAt = expiresAt,
                    daysRemaining = daysRemaining,
                    invoiceHistory = invoices,
                    userProfile = userProfile
                )
            }
            _playerUiState.update { current ->
                current.copy(isVip = isVip)
            }
        }
    }

    fun loadVipSubscriptionPlans() {
        viewModelScope.launch {
            _vipUiState.update { it.copy(isLoading = true, errorMessage = null) }
            val plansResult = repository.getSubscriptionPlans()
            val fallbackPlans = repository.getFallbackSubscriptionPlans()
            val response = plansResult.getOrDefault(fallbackPlans)
            val activePlans = if (response.plans.isNotEmpty()) response.plans else fallbackPlans.plans
            val gateways = if (response.paymentGateways.isNotEmpty()) response.paymentGateways else fallbackPlans.paymentGateways
            val freeCount = response.freeEpisodesCount ?: 1

            _vipUiState.update { current ->
                current.copy(
                    isLoading = false,
                    plans = activePlans,
                    paymentGateways = gateways,
                    selectedPlan = current.selectedPlan ?: activePlans.firstOrNull { it.isPopular } ?: activePlans.firstOrNull()
                )
            }

            _playerUiState.update { current ->
                current.copy(freeEpisodesCount = freeCount)
            }
        }
    }

    fun selectVipPlan(plan: SubscriptionPlanDto) {
        _vipUiState.update { it.copy(selectedPlan = plan) }
    }

    fun submitSubscriptionPayment(
        planId: Any,
        planName: String,
        amount: Double,
        paymentMethod: String,
        senderNumber: String,
        trxId: String,
        notes: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _vipUiState.update { it.copy(isSubmitting = true, submissionMessage = null) }
            val savedUid = repository.getSavedUserId().ifBlank { "USER-${(100000..999999).random()}" }
            val userProfile = repository.getSavedUserProfile()

            val request = SubscriptionSubmitRequest(
                userId = savedUid,
                planId = planId,
                paymentMethod = paymentMethod,
                senderNumber = senderNumber.trim(),
                trxId = trxId.trim(),
                amount = amount,
                planName = planName,
                userName = userProfile?.displayName ?: "PlayDramaFlix Fan",
                userEmail = userProfile?.email,
                userPhone = senderNumber.trim(),
                notes = notes
            )

            val pendingModel = PendingSubscriptionRequestModel(
                userId = savedUid,
                submissionId = "SUB-${(10000..99999).random()}",
                planId = planId.toString(),
                planName = planName,
                amount = amount,
                paymentMethod = paymentMethod,
                senderNumber = senderNumber.trim(),
                transactionId = trxId.trim(),
                timestamp = System.currentTimeMillis(),
                status = "pending"
            )
            repository.savePendingSubscriptionRequest(pendingModel)

            val result = repository.submitSubscription(request)
            _vipUiState.update { it.copy(isSubmitting = false) }

            if (result.isSuccess) {
                val response = result.getOrNull()
                val invoiceId = response?.effectiveInvoiceId ?: pendingModel.submissionId
                val msg = response?.message ?: "Payment submitted successfully! Admin will verify and activate your VIP access."

                val newInvoice = InvoiceItemDto(
                    rawId = invoiceId,
                    submissionId = invoiceId,
                    rawPlanName = planName,
                    rawAmount = amount,
                    rawPaymentMethod = paymentMethod,
                    senderNumber = senderNumber.trim(),
                    rawTrxId = trxId.trim(),
                    rawStatus = "pending",
                    createdAt = "Just now"
                )

                _vipUiState.update { current ->
                    current.copy(
                        submissionSuccess = true,
                        submissionMessage = msg,
                        lastSubmittedInvoiceId = invoiceId,
                        invoiceHistory = listOf(newInvoice) + current.invoiceHistory.filter { it.id != invoiceId }
                    )
                }
                refreshVipStatusAndProfile()
                onComplete(true, msg)
            } else {
                val err = result.exceptionOrNull()?.message ?: "Submission failed. Please check your network and try again."
                _vipUiState.update { it.copy(submissionSuccess = false, submissionMessage = err) }
                onComplete(false, err)
            }
        }
    }

    fun resetSubmissionState() {
        _vipUiState.update { it.copy(submissionSuccess = false, submissionMessage = null, lastSubmittedInvoiceId = null) }
    }

    fun showVipUpgradeModal(targetEpisode: EpisodeDto? = null) {
        _playerUiState.update { it.copy(showVipUpgradeModal = true, lockedEpisodeTarget = targetEpisode) }
    }

    fun dismissVipUpgradeModal() {
        _playerUiState.update { it.copy(showVipUpgradeModal = false, lockedEpisodeTarget = null) }
    }

    fun showEpisodeUnlockModal(targetEpisode: EpisodeDto) {
        _playerUiState.update { it.copy(showEpisodeUnlockModal = true, lockedEpisodeTarget = targetEpisode) }
    }

    fun dismissEpisodeUnlockModal() {
        _playerUiState.update { it.copy(showEpisodeUnlockModal = false, lockedEpisodeTarget = null) }
    }

    fun unlockEpisodeWithRewardAd(context: Context, dramaSlug: String, episode: EpisodeDto) {
        val unlockManager = EpisodeUnlockManager.getInstance(context)
        val unlockHours = UnifiedAdManager.getUnlockDurationHours()
        unlockManager.unlockEpisodeForDuration(dramaSlug, episode.episodeNumber, unlockHours)

        _playerUiState.update { current ->
            val updatedSet = current.unlockedEpisodeNumbers + episode.episodeNumber
            val updatedEpisodes = current.episodes.map { ep ->
                if (ep.episodeNumber == episode.episodeNumber || updatedSet.contains(ep.episodeNumber)) {
                    ep.copy(isLocked = false)
                } else {
                    ep
                }
            }
            val unlockedTarget = episode.copy(isLocked = false)
            current.copy(
                episodes = updatedEpisodes,
                unlockedEpisodeNumbers = updatedSet,
                currentEpisode = unlockedTarget,
                showEpisodeUnlockModal = false,
                showVipUpgradeModal = false,
                lockedEpisodeTarget = null
            )
        }
    }

    // 🎯 মূল হোমস্ক্রিন ডাটা লোড ও লেটেস্ট সর্টিং ইঞ্জিন (FIXED)
    fun loadHomeContent() {
        viewModelScope.launch {
            _homeUiState.update { it.copy(isLoading = true, errorMessage = null) }
            val contentsResult = repository.getContents()
            val plansResult = repository.getSubscriptionPlans()

            val contents = contentsResult.getOrDefault(repository.getFallbackContents())
            val plans = plansResult.getOrNull()?.plans ?: repository.getFallbackSubscriptionPlans().plans

            // 🌟 ১. একদম নতুন রিলিজ হওয়া ড্রামাগুলোকে আইডি ও ডেট অনুযায়ী সর্ট করা
            val sortedByNewest = contents.sortedWith(
                compareByDescending<ContentItemDto> { it.isRecentlyAdded }
                    .thenByDescending { it.id.toLongOrNull() ?: 0L }
            )

            val bangla = sortedByNewest.filter { it.isBanglaDub }
            val hindi = sortedByNewest.filter { it.isHindiDub }
            val shorts = sortedByNewest.filter { it.isShorts }
            val dramaSeries = sortedByNewest.filter { it.isDramaSeries }
            val anime = sortedByNewest.filter { it.isAnime }
            val movies = sortedByNewest.filter { it.isMovie }
            val korean = sortedByNewest.filter {
                it.country.contains("Korea", ignoreCase = true) ||
                        it.categories.any { cat -> cat.contains("k-drama", ignoreCase = true) || cat.contains("korean", ignoreCase = true) }
            }
            val chinese = sortedByNewest.filter {
                it.country.contains("China", ignoreCase = true) ||
                        it.categories.any { cat -> cat.contains("c-drama", ignoreCase = true) || cat.contains("chinese", ignoreCase = true) }
            }

            // 🌟 ২. Recently Added সেকশনে সবার নতুন ড্রামাগুলো আগে দেখানো
            val recentlyAdded = sortedByNewest.take(15)
            val spotlight = sortedByNewest.filter { it.isSpotlight }.ifEmpty { sortedByNewest.take(5) }
            val trending = sortedByNewest.filter { it.isHot || it.viewsCount > 1000 }.ifEmpty { sortedByNewest }

            val activeCategories = buildList {
                add("Home")
                add("Recently Added")
                add("Popular Series")
                if (shorts.isNotEmpty()) add("Shorts Drama")
                if (dramaSeries.isNotEmpty()) add("Drama Series")
                if (anime.isNotEmpty()) add("Anime Series")
                if (movies.isNotEmpty()) add("Movies")
                if (bangla.isNotEmpty()) add("Bangla Dub")
                if (hindi.isNotEmpty()) add("Hindi Dub")
                add("All")
            }

            _homeUiState.update {
                it.copy(
                    isLoading = false,
                    categories = activeCategories,
                    spotlightDramas = spotlight,
                    recentlyAdded = recentlyAdded,
                    banglaDubbed = bangla,
                    hindiDubbed = hindi,
                    trendingDramas = trending,
                    popularDramas = sortedByNewest,
                    dramaSeriesContent = dramaSeries,
                    koreanDramas = korean,
                    chineseDramas = chinese,
                    animeContent = anime,
                    shortsContent = shorts,
                    movieContent = movies,
                    vipPlans = plans
                )
            }

            _searchUiState.update {
                it.copy(
                    allDramas = sortedByNewest,
                    searchResults = sortedByNewest
                )
            }
        }
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            repository.watchlistFlow.collect { entities ->
                val allDramas = _homeUiState.value.popularDramas.ifEmpty { repository.getFallbackContents() }
                val savedList = entities.mapNotNull { entity ->
                    allDramas.find { it.slug == entity.id } ?: ContentItemDto(
                        rawId = entity.id,
                        title = entity.title,
                        slug = entity.id,
                        posterUrl = entity.posterUrl,
                        customDubBadge = entity.dubBadge,
                        rawRating = entity.rating.toString(),
                        rawCategories = entity.category,
                        rawTotalEpisodes = entity.totalEpisodes
                    )
                }
                _watchlistUiState.update { it.copy(savedDramas = savedList) }
            }
        }
    }

    fun loadDramaDetails(slug: String, context: Context? = null) {
        viewModelScope.launch {
            _playerUiState.update { it.copy(isLoading = true, errorMessage = null) }
            val fallbackContent = _homeUiState.value.popularDramas.find { it.slug == slug }
            val detailsResult = repository.getWatchDetails(slug, fallbackContent)

            if (detailsResult.isSuccess) {
                val detail = detailsResult.getOrNull()
                val contentItem = detail?.content ?: fallbackContent
                val rawEps = detail?.episodes ?: emptyList()
                val srvs = detail?.servers ?: emptyList()

                val isUserVip = repository.isUserVip() || _vipUiState.value.isVip
                val freeLimit = _playerUiState.value.freeEpisodesCount.coerceAtLeast(1)
                val unlockManager = context?.let { EpisodeUnlockManager.getInstance(it) }

                val unlockedNumbers = mutableSetOf<Int>()
                rawEps.forEach { ep ->
                    if (unlockManager?.isEpisodeUnlocked(slug, ep.episodeNumber, isUserVip) == true) {
                        unlockedNumbers.add(ep.episodeNumber)
                    }
                }

                val eps = rawEps.mapIndexed { index, ep ->
                    val isEpUnlocked = unlockedNumbers.contains(ep.episodeNumber)
                    if (isUserVip || isEpUnlocked || index < freeLimit) {
                        ep.copy(isLocked = false)
                    } else {
                        ep.copy(isLocked = true)
                    }
                }

                val initialEp = eps.firstOrNull()
                val initialSrv = srvs.firstOrNull()

                val stats = contentItem?.let { repository.getOrCreateDramaStats(it.slug, 120, 1500) }

                _playerUiState.update {
                    it.copy(
                        isLoading = false,
                        content = contentItem,
                        episodes = eps,
                        currentEpisode = initialEp,
                        servers = srvs,
                        selectedServer = initialSrv,
                        isVip = isUserVip,
                        unlockedEpisodeNumbers = unlockedNumbers,
                        likesCount = stats?.likesCount ?: 120,
                        isLiked = stats?.isLiked ?: false,
                        viewsCount = stats?.viewsCount ?: 1500L,
                        recommendations = _homeUiState.value.trendingDramas.filter { rec -> rec.slug != slug }.take(8)
                    )
                }

                contentItem?.let { item ->
                    val statusResult = repository.fetchInteractionStatus(item.id, initialEp?.episodeId)
                    if (statusResult.isSuccess) {
                        val status = statusResult.getOrNull()
                        if (status != null) {
                            _playerUiState.update { current ->
                                current.copy(
                                    likesCount = status.effectiveLikes.toInt(),
                                    isLiked = status.effectiveIsLiked,
                                    viewsCount = status.effectiveViews
                                )
                            }
                        }
                    }
                }

                contentItem?.let { item ->
                    repository.recordVideoInteractionView(item.id)
                }

                refreshComments()
            } else {
                _playerUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not load drama details"
                    )
                }
            }
        }
    }

    fun selectEpisode(episode: EpisodeDto) {
        _playerUiState.update { it.copy(currentEpisode = episode) }
    }

    fun selectServer(server: ServerDto) {
        _playerUiState.update { it.copy(selectedServer = server) }
    }

    fun playNextEpisode() {
        val currentState = _playerUiState.value
        val currentIndex = currentState.episodes.indexOfFirst { it.episodeNumber == currentState.currentEpisode?.episodeNumber }
        if (currentIndex in 0 until currentState.episodes.size - 1) {
            val nextEp = currentState.episodes[currentIndex + 1]
            selectEpisode(nextEp)
        }
    }

    fun playPreviousEpisode() {
        val currentState = _playerUiState.value
        val currentIndex = currentState.episodes.indexOfFirst { it.episodeNumber == currentState.currentEpisode?.episodeNumber }
        if (currentIndex > 0) {
            val prevEp = currentState.episodes[currentIndex - 1]
            selectEpisode(prevEp)
        }
    }

    fun updateWatchProgress(progressMs: Long, durationMs: Long) {
        val content = _playerUiState.value.content ?: return
        val episode = _playerUiState.value.currentEpisode ?: return
        viewModelScope.launch {
            repository.saveWatchProgress(content, episode, progressMs, durationMs)
        }
    }

    fun toggleWatchlist() {
        val content = _playerUiState.value.content ?: return
        val currentlySaved = _playerUiState.value.isInWatchlist
        viewModelScope.launch {
            repository.toggleWatchlist(content, currentlySaved)
            _playerUiState.update { it.copy(isInWatchlist = !currentlySaved) }
        }
    }

    fun toggleLikeDrama() {
        val content = _playerUiState.value.content ?: return
        val currentLiked = _playerUiState.value.isLiked
        val currentLikesCount = _playerUiState.value.likesCount
        val newLiked = !currentLiked
        val optimisticCount = if (newLiked) currentLikesCount + 1 else maxOf(0, currentLikesCount - 1)

        _playerUiState.update {
            it.copy(
                isLiked = newLiked,
                likesCount = optimisticCount
            )
        }

        viewModelScope.launch {
            val serverResult = repository.toggleInteractionLike(
                contentId = content.id,
                episodeId = _playerUiState.value.currentEpisode?.episodeId
            )
            if (serverResult.isSuccess) {
                val resp = serverResult.getOrNull()
                if (resp != null) {
                    _playerUiState.update {
                        it.copy(
                            isLiked = resp.isLiked ?: newLiked,
                            likesCount = resp.totalLikes?.toInt() ?: resp.likes?.toInt() ?: optimisticCount
                        )
                    }
                }
            } else {
                val updated = repository.toggleOrganicLike(content.slug, currentLikesCount)
                _playerUiState.update {
                    it.copy(
                        isLiked = updated.isLiked,
                        likesCount = updated.likesCount
                    )
                }
            }
            loadUserActivity(isRefresh = true)
        }
    }

    fun refreshComments() {
        val content = _playerUiState.value.content ?: return
        viewModelScope.launch {
            _playerUiState.update { it.copy(isCommentsLoading = true) }
            val commentsResult = repository.fetchCommentsList(content.id, _playerUiState.value.currentEpisode?.episodeId)
            val list = commentsResult.getOrDefault(emptyList())
            _playerUiState.update {
                it.copy(
                    isCommentsLoading = false,
                    comments = list
                )
            }
        }
    }

    fun postComment(commentText: String, parentId: String? = null) {
        val content = _playerUiState.value.content ?: return
        val currentComments = _playerUiState.value.comments
        viewModelScope.launch {
            _playerUiState.update { it.copy(isPostingComment = true) }
            val result = repository.postNewComment(
                contentId = content.id,
                episodeId = _playerUiState.value.currentEpisode?.episodeId,
                parentId = parentId,
                commentText = commentText
            )
            val newComment = result.getOrNull()
            if (newComment != null) {
                if (parentId == null) {
                    _playerUiState.update {
                        it.copy(
                            isPostingComment = false,
                            comments = listOf(newComment) + it.comments
                        )
                    }
                } else {
                    val updatedList = currentComments.map { rootComment ->
                        if (rootComment.id == parentId) {
                            val updatedReplies = rootComment.repliesList + newComment
                            rootComment.copy(
                                replies = updatedReplies,
                                rawRepliesCount = updatedReplies.size
                            )
                        } else {
                            rootComment
                        }
                    }
                    _playerUiState.update {
                        it.copy(
                            isPostingComment = false,
                            comments = updatedList
                        )
                    }
                }
            } else {
                _playerUiState.update { it.copy(isPostingComment = false) }
            }
            loadUserActivity(isRefresh = true)
        }
    }

    fun toggleCommentLike(commentId: String) {
        val currentComments = _playerUiState.value.comments
        val updatedComments = currentComments.map { root ->
            if (root.id == commentId) {
                val newLiked = !root.isLiked
                val newCount = if (newLiked) root.likesCount + 1 else maxOf(0, root.likesCount - 1)
                root.copy(isLikedVal = newLiked, rawLikesCount = newCount)
            } else {
                val updatedReplies = root.repliesList.map { reply ->
                    if (reply.id == commentId) {
                        val newLiked = !reply.isLiked
                        val newCount = if (newLiked) reply.likesCount + 1 else maxOf(0, reply.likesCount - 1)
                        reply.copy(isLikedVal = newLiked, rawLikesCount = newCount)
                    } else {
                        reply
                    }
                }
                root.copy(replies = updatedReplies)
            }
        }
        _playerUiState.update { it.copy(comments = updatedComments) }

        viewModelScope.launch {
            repository.toggleCommentLike(commentId)
        }
    }

    fun recordCommentShare(commentId: String) {
        viewModelScope.launch {
            repository.recordCommentShare(commentId)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchUiState.update {
            val filtered = if (query.isBlank()) {
                it.allDramas
            } else {
                it.allDramas.filter { drama ->
                    drama.title.contains(query, ignoreCase = true) ||
                            drama.categories.any { cat -> cat.contains(query, ignoreCase = true) } ||
                            drama.dubBadge.contains(query, ignoreCase = true) ||
                            drama.country.contains(query, ignoreCase = true)
                }
            }
            it.copy(searchQuery = query, searchResults = filtered)
        }
    }

    fun selectSearchTag(tag: String) {
        _searchUiState.update {
            val isSame = it.selectedFilterTag == tag
            val newTag = if (isSame) null else tag
            val filtered = if (newTag == null) {
                it.allDramas
            } else {
                it.allDramas.filter { drama ->
                    drama.categories.any { cat -> cat.contains(tag, ignoreCase = true) } ||
                            drama.dubBadge.contains(tag, ignoreCase = true) ||
                            drama.title.contains(tag, ignoreCase = true) ||
                            drama.type.contains(tag, ignoreCase = true)
                }
            }
            it.copy(selectedFilterTag = newTag, searchResults = filtered)
        }
    }

    fun checkAppVersion() {
        viewModelScope.launch {
            val versionResult = repository.checkAppVersion()
            if (versionResult.isSuccess) {
                val info = versionResult.getOrNull()
                if (info?.updateAvailable == true) {
                    _updateUiState.update {
                        it.copy(showDialog = true, updateInfo = info)
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateUiState.update { it.copy(showDialog = false) }
    }

    fun refreshAuthState() {
        val isLoggedIn = repository.isUserLoggedIn()
        val userProfile = repository.getSavedUserProfile()
        val isVip = repository.isUserVip()
        _authUiState.update {
            it.copy(
                isLoggedIn = isLoggedIn,
                userProfile = userProfile,
                isVip = isVip
            )
        }
    }

    fun showAuthDialog(show: Boolean) {
        _authUiState.update { it.copy(showAuthDialog = show, errorMessage = null, authMessage = null) }
    }

    fun clearAuthMessage() {
        _authUiState.update { it.copy(authMessage = null, errorMessage = null) }
    }

    fun signInWithGoogle(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        _authUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val googleResult = GoogleAuthManager.signIn(context)
                if (googleResult.isSuccess) {
                    val authReq = googleResult.getOrNull()!!

                    val backendResult = repository.authenticateWithGoogle(
                        googleId = authReq.googleId,
                        email = authReq.email,
                        name = authReq.name,
                        avatar = authReq.avatar
                    )

                    if (backendResult.isSuccess) {
                        val authResp = backendResult.getOrNull()!!
                        val user = authResp.user ?: repository.getSavedUserProfile()
                        val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)
                        _authUiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                userProfile = user,
                                isVip = isVip,
                                authMessage = authResp.message ?: "Google Sign-In successful!",
                                showAuthDialog = false
                            )
                        }
                        refreshVipStatusAndProfile()
                        loadUserActivity(isRefresh = true)
                        onComplete?.invoke(true)
                    } else {
                        val err = backendResult.exceptionOrNull()?.message ?: "Backend authentication failed"
                        _authUiState.update { it.copy(isLoading = false, errorMessage = err) }
                        onComplete?.invoke(false)
                    }
                } else {
                    val exception = googleResult.exceptionOrNull()
                    val isCancellation = exception is androidx.credentials.exceptions.GetCredentialCancellationException
                    if (isCancellation) {
                        _authUiState.update { it.copy(isLoading = false) }
                        onComplete?.invoke(false)
                    } else {
                        _authUiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = "Google One-Tap dialog not available on this device. Please use the Google Email Sign-In / Register below."
                            )
                        }
                        onComplete?.invoke(false)
                    }
                }
            } catch (e: Exception) {
                _authUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Sign-In error: ${e.message}. You can sign in using your Google email below."
                    )
                }
                onComplete?.invoke(false)
            }
        }
    }

    fun signInOrRegisterWithGoogleEmail(
        email: String,
        name: String? = null,
        avatar: String? = null,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
            _authUiState.update { it.copy(errorMessage = "Please enter a valid Google email address.") }
            onComplete?.invoke(false)
            return
        }

        val displayName = if (!name.isNullOrBlank()) {
            name.trim()
        } else {
            trimmedEmail.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
        }

        val googleId = "gid_${Math.abs(trimmedEmail.lowercase().hashCode())}"
        val userAvatar = avatar ?: "https://lh3.googleusercontent.com/a/default-user"

        authenticateGoogleDirect(
            googleId = googleId,
            email = trimmedEmail,
            name = displayName,
            avatar = userAvatar,
            onComplete = onComplete
        )
    }

    fun authenticateGoogleDirect(
        googleId: String,
        email: String,
        name: String,
        avatar: String?,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        _authUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val backendResult = repository.authenticateWithGoogle(
                googleId = googleId,
                email = email,
                name = name,
                avatar = avatar
            )

            if (backendResult.isSuccess) {
                val authResp = backendResult.getOrNull()!!
                val user = authResp.user ?: repository.getSavedUserProfile()
                val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)
                _authUiState.update {
                    it.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        userProfile = user,
                        isVip = isVip,
                        authMessage = authResp.message ?: "Google Authentication successful!",
                        showAuthDialog = false
                    )
                }
                refreshVipStatusAndProfile()
                loadUserActivity(isRefresh = true)
                onComplete?.invoke(true)
            } else {
                val err = backendResult.exceptionOrNull()?.message ?: "Authentication failed"
                _authUiState.update { it.copy(isLoading = false, errorMessage = err) }
                onComplete?.invoke(false)
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            GoogleAuthManager.signOut(context)
            repository.clearUserSession()
            _authUiState.update {
                it.copy(
                    isLoggedIn = false,
                    userProfile = null,
                    isVip = false,
                    authMessage = "Signed out successfully"
                )
            }
            refreshVipStatusAndProfile()
            _activityUiState.update { ActivityUiState() }
        }
    }
}

class DramaFlixViewModelFactory(
    private val repository: PlayDramaFlixRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DramaFlixViewModel::class.java)) {
            return DramaFlixViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
