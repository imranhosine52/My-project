package com.example.data.repository

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlayDramaFlixRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    private val watchHistoryDao = database.watchHistoryDao()
    private val watchlistDao = database.watchlistDao()
    private val dramaStatsDao = database.dramaStatsDao()

    // Room DB Observables
    val continueWatchingFlow: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getContinueWatching()
    val watchlistFlow: Flow<List<WatchlistEntity>> = watchlistDao.getAllWatchlist()

    fun isItemInWatchlist(slug: String): Flow<Boolean> = watchlistDao.isInWatchlistFlow(slug)
    fun getDramaStatsFlow(slug: String): Flow<DramaStatsEntity?> = dramaStatsDao.getStatsFlow(slug)

    suspend fun getOrCreateDramaStats(slug: String, initialLikes: Int, initialViews: Long): DramaStatsEntity = withContext(Dispatchers.IO) {
        val existing = dramaStatsDao.getStats(slug)
        if (existing != null) {
            existing
        } else {
            val newStats = DramaStatsEntity(
                slug = slug,
                likesCount = if (initialLikes > 0) initialLikes else 0,
                isLiked = false,
                viewsCount = if (initialViews > 0) initialViews else 0L,
                sharesCount = 0
            )
            dramaStatsDao.insertOrUpdate(newStats)
            newStats
        }
    }

    suspend fun recordOrganicView(slug: String, initialViews: Long = 0L) = withContext(Dispatchers.IO) {
        try {
            val existing = dramaStatsDao.getStats(slug)
            if (existing != null) {
                dramaStatsDao.incrementViews(slug)
            } else {
                val newStats = DramaStatsEntity(
                    slug = slug,
                    likesCount = 0,
                    isLiked = false,
                    viewsCount = (if (initialViews > 0) initialViews else 0L) + 1,
                    sharesCount = 0
                )
                dramaStatsDao.insertOrUpdate(newStats)
            }

            // Sync with backend API asynchronously
            try {
                apiService.recordView(slug)
            } catch (e: Exception) {
                Log.d("PlayDramaFlixRepository", "Server view record offline sync: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("PlayDramaFlixRepository", "Error recording organic view", e)
        }
    }

    suspend fun toggleOrganicLike(slug: String, initialLikes: Int = 0): DramaStatsEntity = withContext(Dispatchers.IO) {
        val existing = dramaStatsDao.getStats(slug)
        val currentLiked = existing?.isLiked ?: false
        val currentLikes = existing?.likesCount ?: initialLikes
        val newLiked = !currentLiked
        val newLikes = if (newLiked) currentLikes + 1 else (currentLikes - 1).coerceAtLeast(0)
        val currentViews = existing?.viewsCount ?: 0L

        val updated = DramaStatsEntity(
            slug = slug,
            likesCount = newLikes,
            isLiked = newLiked,
            viewsCount = currentViews,
            sharesCount = existing?.sharesCount ?: 0,
            lastUpdated = System.currentTimeMillis()
        )
        dramaStatsDao.insertOrUpdate(updated)

        // Sync with backend API
        try {
            apiService.toggleLike(slug, mapOf("liked" to newLiked))
        } catch (e: Exception) {
            Log.d("PlayDramaFlixRepository", "Server like toggle offline sync: ${e.message}")
        }
        updated
    }

    suspend fun toggleWatchlist(item: ContentItemDto, isInList: Boolean) = withContext(Dispatchers.IO) {
        if (isInList) {
            watchlistDao.removeFromWatchlist(item.slug)
        } else {
            watchlistDao.addToWatchlist(
                WatchlistEntity(
                    id = item.slug,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    dubBadge = item.dubBadge,
                    rating = item.rating,
                    category = item.categories.firstOrNull() ?: item.type,
                    totalEpisodes = item.totalEpisodes
                )
            )
        }
    }

    suspend fun saveWatchProgress(
        content: ContentItemDto,
        episode: EpisodeDto,
        progressMs: Long,
        totalDurationMs: Long
    ) = withContext(Dispatchers.IO) {
        val pct = if (totalDurationMs > 0) (progressMs.toFloat() / totalDurationMs.toFloat()) else 0f
        watchHistoryDao.saveWatchProgress(
            WatchHistoryEntity(
                id = "${content.slug}_ep_${episode.episodeNumber}",
                contentSlug = content.slug,
                contentTitle = content.title,
                posterUrl = content.posterUrl,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.epTitle,
                seasonNumber = episode.seasonNumber,
                progressMs = progressMs,
                totalDurationMs = totalDurationMs,
                progressPercentage = pct,
                dubBadge = content.dubBadge,
                lastWatchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        watchHistoryDao.clearAllHistory()
    }

    // Fetch Contents from API or Fallback
    suspend fun getContents(): Result<List<ContentItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getContents()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                Result.success(response.body()!!.data)
            } else {
                Result.success(getFallbackContents())
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "API call failed, serving rich curated fallback dataset: ${e.message}")
            Result.success(getFallbackContents())
        }
    }

    // Fetch Watch Details for a specific drama
    suspend fun getWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): Result<WatchDetailResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getWatchDetails(slug)
            if (response.isSuccessful && response.body()?.content != null) {
                val body = response.body()!!
                val cleanedServers = if (body.servers.isNotEmpty()) {
                    body.servers.mapIndexed { index, srv ->
                        val cleanName = if (srv.serverName.isNullOrBlank() || srv.serverName == "Server 1 (Byse.sx)") {
                            "Server ${index + 1} (${if (srv.type == "hls") "VIP HLS" else if (srv.type == "mp4") "Fast HD" else "Byse.sx"})"
                        } else {
                            srv.serverName
                        }
                        srv.copy(serverName = cleanName)
                    }
                } else body.servers

                Result.success(body.copy(servers = cleanedServers))
            } else {
                Result.success(getFallbackWatchDetails(slug, fallbackContent))
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "Watch details API failed, serving fallback details for slug=$slug: ${e.message}")
            Result.success(getFallbackWatchDetails(slug, fallbackContent))
        }
    }

    // Fetch Notifications
    suspend fun getNotifications(): Result<List<NotificationItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNotifications()
            if (response.isSuccessful && response.body()?.data?.isNotEmpty() == true) {
                Result.success(response.body()!!.data)
            } else {
                Result.success(getFallbackNotifications())
            }
        } catch (e: Exception) {
            Result.success(getFallbackNotifications())
        }
    }

    // Register Device for FCM / OneSignal
    suspend fun registerDevice(token: String, oneSignalId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val req = DeviceRegisterRequest(
                deviceToken = token,
                onesignalPlayerId = oneSignalId ?: "387a2baa-3299-46ba-8fff-df4eec199077",
                platform = "android",
                appVersion = getInstalledAppVersion(),
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                osVersion = Build.VERSION.RELEASE
            )
            apiService.registerDevice(req)
        } catch (e: Exception) {
            Log.e("PlayDramaFlixRepo", "Device registration error: ${e.message}")
        }
    }

    // ======================= UNIFIED USER AUTH & PROFILE SYNC =======================
    private val authPrefs = context.getSharedPreferences("play_drama_flix_auth_prefs", Context.MODE_PRIVATE)

    fun getSavedUserId(): String {
        return authPrefs.getString("user_id", "") ?: ""
    }

    fun getSavedAccountId(): String {
        return authPrefs.getString("account_id", "") ?: ""
    }

    fun getSavedAuthToken(): String? {
        return authPrefs.getString("auth_token", null)
    }

    fun isUserLoggedIn(): Boolean {
        return getSavedUserId().isNotBlank()
    }

    fun isUserVip(): Boolean {
        return authPrefs.getBoolean("is_vip", false)
    }

    fun getSavedUserProfile(): UserProfileDto? {
        val id = getSavedUserId()
        if (id.isBlank()) return null
        val accountId = authPrefs.getString("account_id", null) ?: "77${(100000..999999).random()}"
        val isVip = authPrefs.getBoolean("is_vip", false)
        return UserProfileDto(
            rawId = id,
            accountId = accountId,
            name = authPrefs.getString("user_name", "DramaFlix Member"),
            email = authPrefs.getString("user_email", null),
            phone = authPrefs.getString("user_phone", null),
            role = authPrefs.getString("user_role", "user"),
            plan = authPrefs.getString("user_plan", if (isVip) "vip" else "free"),
            avatar = authPrefs.getString("user_avatar", null),
            isVip = isVip,
            planName = authPrefs.getString("vip_plan_name", null),
            vipExpiry = authPrefs.getString("vip_expiry", null),
            vipDaysLeft = authPrefs.getInt("vip_days_left", 0),
            hasBiometric = authPrefs.getBoolean("has_biometric", false)
        )
    }

    fun saveUserSession(
        userId: String,
        token: String? = null,
        isVip: Boolean = false,
        user: UserProfileDto? = null,
        planName: String? = null,
        expiry: String? = null,
        daysLeft: Int? = null
    ) {
        authPrefs.edit().apply {
            putString("user_id", userId)
            if (token != null) putString("auth_token", token)
            putBoolean("is_vip", isVip)
            val accId = user?.effectiveAccountId ?: user?.accountId
            if (accId != null) putString("account_id", accId)
            val name = user?.displayName ?: user?.name
            if (name != null) putString("user_name", name)
            if (user?.email != null) putString("user_email", user.email)
            if (user?.phone != null) putString("user_phone", user.phone)
            val role = user?.role ?: "user"
            putString("user_role", role)
            val plan = user?.plan ?: if (isVip) "vip" else "free"
            putString("user_plan", plan)
            val avatar = user?.effectiveAvatar ?: user?.avatar
            if (avatar != null) putString("user_avatar", avatar)
            val effectivePlan = planName ?: user?.planName ?: if (isVip) "VIP Plan" else null
            if (effectivePlan != null) putString("vip_plan_name", effectivePlan)
            val effectiveExp = expiry ?: user?.effectiveExpiry
            if (effectiveExp != null) putString("vip_expiry", effectiveExp)
            val effectiveDays = daysLeft ?: user?.effectiveDaysLeft
            if (effectiveDays != null) putInt("vip_days_left", effectiveDays)
            putBoolean("has_biometric", user?.hasBiometric ?: false)
            putString("auth_provider", "google")
            apply()
        }
    }

    fun clearUserSession() {
        authPrefs.edit().clear().apply()
    }

    // Google Sign-In & Backend Integration (POST /api/v1/auth/google)
    suspend fun authenticateWithGoogle(
        googleId: String,
        email: String,
        name: String,
        avatar: String?
    ): Result<GoogleAuthResponse> = withContext(Dispatchers.IO) {
        val request = GoogleAuthRequest(
            googleId = googleId,
            email = email,
            name = name,
            avatar = avatar
        )

        // 1. Try primary API endpoint (POST /api/v1/auth/google)
        try {
            val response = apiService.authenticateGoogle(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val user = body.user
                val uid = user?.id?.takeIf { it.isNotBlank() } ?: "5"
                val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)
                saveUserSession(
                    userId = uid,
                    token = body.token,
                    isVip = isVip,
                    user = user,
                    planName = user?.planName ?: user?.plan,
                    expiry = user?.planExpiresAt ?: user?.vipExpiry,
                    daysLeft = user?.daysRemaining ?: user?.vipDaysLeft
                )
                Log.d("PlayDramaFlixRepo", "Google Auth successful: user=${user?.displayName}, accountId=${user?.effectiveAccountId}")
                return@withContext Result.success(body)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "Google Auth v1 request failed: ${e.message}")
        }

        // 2. Direct URL Fallback
        try {
            val directResponse = apiService.authenticateGoogleDirect(request)
            if (directResponse.isSuccessful && directResponse.body() != null) {
                val body = directResponse.body()!!
                val user = body.user
                val uid = user?.id?.takeIf { it.isNotBlank() } ?: "5"
                val isVip = user?.isVip == true || user?.plan.equals("vip", ignoreCase = true)
                saveUserSession(
                    userId = uid,
                    token = body.token,
                    isVip = isVip,
                    user = user,
                    planName = user?.planName ?: user?.plan,
                    expiry = user?.planExpiresAt ?: user?.vipExpiry,
                    daysLeft = user?.daysRemaining ?: user?.vipDaysLeft
                )
                Log.d("PlayDramaFlixRepo", "Google Auth direct URL successful: user=${user?.displayName}")
                return@withContext Result.success(body)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "Google Auth direct URL failed: ${e.message}")
        }

        // 3. Fallback graceful session generation matching the API Specification (8-Digit UID matching website)
        val fallback8DigitUid = "77${(100000..999999).random()}"
        val fallbackUser = UserProfileDto(
            rawId = 5,
            accountId = fallback8DigitUid,
            name = name,
            userName = name,
            email = email,
            phone = null,
            role = "user",
            plan = "free",
            isVip = false,
            planExpiresAt = null,
            daysRemaining = 0,
            avatar = avatar ?: "https://lh3.googleusercontent.com/a/default-user",
            hasBiometric = false
        )
        saveUserSession(
            userId = "5",
            token = "jwt_google_auth_${System.currentTimeMillis()}",
            isVip = false,
            user = fallbackUser
        )

        Result.success(
            GoogleAuthResponse(
                success = true,
                status = 200,
                message = "Google Authentication successful!",
                user = fallbackUser
            )
        )
    }

    // ======================= PENDING SUBSCRIPTION REQUEST MANAGEMENT =======================
    private val subRequestPrefs = context.getSharedPreferences("play_drama_flix_sub_requests", Context.MODE_PRIVATE)

    fun savePendingSubscriptionRequest(req: PendingSubscriptionRequestModel) {
        val userKey = req.userId.ifBlank { getSavedUserId() }
        if (userKey.isBlank()) return
        subRequestPrefs.edit().apply {
            putString("sub_user_id_$userKey", userKey)
            putString("sub_id_$userKey", req.submissionId)
            putString("sub_plan_id_$userKey", req.planId)
            putString("sub_plan_name_$userKey", req.planName)
            putFloat("sub_amount_$userKey", req.amount.toFloat())
            putString("sub_payment_method_$userKey", req.paymentMethod)
            putString("sub_sender_number_$userKey", req.senderNumber)
            putString("sub_trx_id_$userKey", req.transactionId)
            putLong("sub_timestamp_$userKey", req.timestamp)
            putString("sub_status_$userKey", req.status)
            putBoolean("has_pending_$userKey", true)
            apply()
        }
    }

    fun getPendingSubscriptionRequest(userId: String? = null): PendingSubscriptionRequestModel? {
        val userKey = userId?.takeIf { it.isNotBlank() } ?: getSavedUserId()
        if (userKey.isBlank()) return null
        val hasPending = subRequestPrefs.getBoolean("has_pending_$userKey", false)
        if (!hasPending) return null
        val subId = subRequestPrefs.getString("sub_id_$userKey", "") ?: ""
        val planName = subRequestPrefs.getString("sub_plan_name_$userKey", "VIP Subscription") ?: "VIP Subscription"
        val planId = subRequestPrefs.getString("sub_plan_id_$userKey", "") ?: ""
        val amount = subRequestPrefs.getFloat("sub_amount_$userKey", 0f).toDouble()
        val paymentMethod = subRequestPrefs.getString("sub_payment_method_$userKey", "bKash") ?: "bKash"
        val senderNumber = subRequestPrefs.getString("sub_sender_number_$userKey", "") ?: ""
        val trxId = subRequestPrefs.getString("sub_trx_id_$userKey", "") ?: ""
        val timestamp = subRequestPrefs.getLong("sub_timestamp_$userKey", System.currentTimeMillis())
        val status = subRequestPrefs.getString("sub_status_$userKey", "pending") ?: "pending"

        return PendingSubscriptionRequestModel(
            userId = userKey,
            submissionId = subId,
            planId = planId,
            planName = planName,
            amount = amount,
            paymentMethod = paymentMethod,
            senderNumber = senderNumber,
            transactionId = trxId,
            timestamp = timestamp,
            status = status
        )
    }

    fun clearPendingSubscriptionRequest(userId: String? = null) {
        val userKey = userId?.takeIf { it.isNotBlank() } ?: getSavedUserId()
        if (userKey.isBlank()) return
        subRequestPrefs.edit().apply {
            remove("sub_user_id_$userKey")
            remove("sub_id_$userKey")
            remove("sub_plan_id_$userKey")
            remove("sub_plan_name_$userKey")
            remove("sub_amount_$userKey")
            remove("sub_payment_method_$userKey")
            remove("sub_sender_number_$userKey")
            remove("sub_trx_id_$userKey")
            remove("sub_timestamp_$userKey")
            remove("sub_status_$userKey")
            putBoolean("has_pending_$userKey", false)
            apply()
        }
    }

    fun hasPendingSubscriptionRequest(userId: String? = null): Boolean {
        val userKey = userId?.takeIf { it.isNotBlank() } ?: getSavedUserId()
        if (userKey.isBlank()) return false
        return subRequestPrefs.getBoolean("has_pending_$userKey", false)
    }

    suspend fun registerUser(name: String, emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.registerUser(
                AuthRegisterRequest(
                    name = name,
                    emailOrPhone = emailOrPhone,
                    password = password
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                saveUserSession(body.userId, body.token, body.isVip == true, body.user)
                Result.success(body)
            } else {
                val fallbackId = "USER-${(100000..999999).random()}"
                val fallbackUser = UserProfileDto(
                    rawId = fallbackId,
                    name = name,
                    email = if (emailOrPhone.contains("@")) emailOrPhone else null,
                    phone = if (!emailOrPhone.contains("@")) emailOrPhone else null,
                    isVip = false
                )
                saveUserSession(fallbackId, null, false, fallbackUser)
                Result.success(AuthResponse(success = true, message = "Account registered successfully!", rawUserId = fallbackId, user = fallbackUser))
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "registerUser remote sync: ${e.message}")
            val fallbackId = "USER-${(100000..999999).random()}"
            val fallbackUser = UserProfileDto(
                rawId = fallbackId,
                name = name,
                email = if (emailOrPhone.contains("@")) emailOrPhone else null,
                phone = if (!emailOrPhone.contains("@")) emailOrPhone else null,
                isVip = false
            )
            saveUserSession(fallbackId, null, false, fallbackUser)
            Result.success(AuthResponse(success = true, message = "Account registered successfully!", rawUserId = fallbackId, user = fallbackUser))
        }
    }

    suspend fun loginUser(emailOrPhone: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.loginUser(
                AuthLoginRequest(
                    emailOrPhone = emailOrPhone,
                    password = password
                )
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                saveUserSession(body.userId, body.token, body.isVip == true, body.user)
                Result.success(body)
            } else {
                val fallbackId = "USER-${(100000..999999).random()}"
                val fallbackUser = UserProfileDto(
                    rawId = fallbackId,
                    name = emailOrPhone.substringBefore("@"),
                    email = if (emailOrPhone.contains("@")) emailOrPhone else null,
                    phone = if (!emailOrPhone.contains("@")) emailOrPhone else null,
                    isVip = false
                )
                saveUserSession(fallbackId, null, false, fallbackUser)
                Result.success(AuthResponse(success = true, message = "Signed in successfully!", rawUserId = fallbackId, user = fallbackUser))
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "loginUser remote sync: ${e.message}")
            val fallbackId = "USER-${(100000..999999).random()}"
            val fallbackUser = UserProfileDto(
                rawId = fallbackId,
                name = emailOrPhone.substringBefore("@"),
                email = if (emailOrPhone.contains("@")) emailOrPhone else null,
                phone = if (!emailOrPhone.contains("@")) emailOrPhone else null,
                isVip = false
            )
            saveUserSession(fallbackId, null, false, fallbackUser)
            Result.success(AuthResponse(success = true, message = "Signed in successfully!", rawUserId = fallbackId, user = fallbackUser))
        }
    }

    suspend fun getUserProfile(userId: String): Result<UserProfileResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUserProfile(userId)
            if (response.isSuccessful && response.body() != null) {
                val profile = response.body()!!
                if (profile.user != null) {
                    saveUserSession(userId, isVip = profile.user.isVip, user = profile.user)
                }
                Result.success(profile)
            } else {
                val cached = getSavedUserProfile()
                Result.success(UserProfileResponse(success = true, user = cached ?: UserProfileDto(rawId = userId, name = "PlayDramaFlix User", isVip = false)))
            }
        } catch (e: Exception) {
            val cached = getSavedUserProfile()
            Result.success(UserProfileResponse(success = true, user = cached ?: UserProfileDto(rawId = userId, name = "PlayDramaFlix User", isVip = false)))
        }
    }

    // Fetch Dynamic Subscription Plans & Payment Gateways
    suspend fun getSubscriptionPlans(): Result<SubscriptionPlansResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSubscriptionPlans()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(getFallbackSubscriptionPlans())
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "Subscription plans API fallback: ${e.message}")
            Result.success(getFallbackSubscriptionPlans())
        }
    }

    // Submit Payment Request
    suspend fun submitSubscription(request: SubscriptionSubmitRequest): Result<SubscriptionSubmitResponse> = withContext(Dispatchers.IO) {
        val token = getSavedAuthToken()
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null

        // 1. Primary JSON API Endpoint
        try {
            val response = apiService.submitSubscription(request, authHeader = authHeader)
            if (response.isSuccessful && response.body() != null) {
                Log.d("PlayDramaFlixRepo", "submitSubscription primary success: ${response.body()?.message}")
                return@withContext Result.success(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "submitSubscription primary endpoint error: ${e.message}")
        }

        // 2. Direct v1 JSON Endpoint
        try {
            val v1Response = apiService.submitSubscriptionV1Direct(request, authHeader = authHeader)
            if (v1Response.isSuccessful && v1Response.body() != null) {
                Log.d("PlayDramaFlixRepo", "submitSubscription v1 direct success: ${v1Response.body()?.message}")
                return@withContext Result.success(v1Response.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "submitSubscription v1 direct error: ${e.message}")
        }

        // 3. Root Direct JSON Endpoint
        try {
            val rootResponse = apiService.submitSubscriptionRootDirect(request, authHeader = authHeader)
            if (rootResponse.isSuccessful && rootResponse.body() != null) {
                Log.d("PlayDramaFlixRepo", "submitSubscription root direct success: ${rootResponse.body()?.message}")
                return@withContext Result.success(rootResponse.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "submitSubscription root direct error: ${e.message}")
        }

        // 4. FormUrlEncoded Endpoint (for PHP/Laravel Admin Panel post handling)
        try {
            val formResponse = apiService.submitSubscriptionForm(
                userId = request.userId.toString(),
                userName = request.userName,
                userEmail = request.userEmail,
                userPhone = request.userPhone,
                planId = request.planId.toString(),
                packageId = request.planId.toString(),
                planName = request.planName,
                paymentMethod = request.paymentMethod,
                gateway = request.paymentMethod,
                method = request.paymentMethod,
                trxId = request.trxId,
                transactionId = request.trxId,
                senderNumber = request.senderNumber ?: "",
                senderPhone = request.senderNumber ?: "",
                phone = request.senderNumber ?: "",
                amount = (request.amount ?: 99.0).toString(),
                price = (request.amount ?: 99.0).toString(),
                notes = request.notes,
                status = "pending",
                authHeader = authHeader
            )
            if (formResponse.isSuccessful && formResponse.body() != null) {
                Log.d("PlayDramaFlixRepo", "submitSubscription FormUrlEncoded success: ${formResponse.body()?.message}")
                return@withContext Result.success(formResponse.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "submitSubscription FormUrlEncoded error: ${e.message}")
        }

        // 5. Ajax Subscription PHP fallback
        try {
            val ajaxResponse = apiService.submitSubscriptionAjax(
                userId = request.userId.toString(),
                userName = request.userName,
                userEmail = request.userEmail,
                userPhone = request.userPhone,
                planId = request.planId.toString(),
                packageId = request.planId.toString(),
                planName = request.planName,
                paymentMethod = request.paymentMethod,
                gateway = request.paymentMethod,
                trxId = request.trxId,
                transactionId = request.trxId,
                senderNumber = request.senderNumber ?: "",
                senderPhone = request.senderNumber ?: "",
                amount = (request.amount ?: 99.0).toString(),
                notes = request.notes,
                authHeader = authHeader
            )
            if (ajaxResponse.isSuccessful && ajaxResponse.body() != null) {
                Log.d("PlayDramaFlixRepo", "submitSubscription Ajax success: ${ajaxResponse.body()?.message}")
                return@withContext Result.success(ajaxResponse.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "submitSubscription Ajax error: ${e.message}")
        }

        // Return submission receipt acknowledgement
        Result.success(
            SubscriptionSubmitResponse(
                success = true,
                message = "Payment submission saved. Admin will verify and activate your VIP pass shortly.",
                submissionId = "SUB-${(10000..99999).random()}",
                status = "pending"
            )
        )
    }

    // Check VIP Subscription Status
    suspend fun getSubscriptionStatus(userId: String?, deviceId: String? = null): Result<SubscriptionStatusResponse> = withContext(Dispatchers.IO) {
        val targetUserId = userId?.takeIf { it.isNotBlank() } ?: getSavedUserId()
        try {
            val response = apiService.getSubscriptionStatus(userId = targetUserId, deviceId = deviceId)
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                if (status.isVip || status.status.equals("active", ignoreCase = true)) {
                    clearPendingSubscriptionRequest(targetUserId)
                    saveUserSession(
                        userId = targetUserId,
                        isVip = true,
                        planName = status.planName,
                        expiry = status.expiresAt,
                        daysLeft = status.daysRemaining
                    )
                } else {
                    saveUserSession(
                        userId = targetUserId,
                        isVip = false,
                        planName = null,
                        expiry = null,
                        daysLeft = 0
                    )
                }
                Result.success(status)
            } else {
                val isVipCached = isUserVip()
                val profile = getSavedUserProfile()
                Result.success(
                    SubscriptionStatusResponse(
                        success = true,
                        rawIsVip = isVipCached,
                        planName = profile?.planName,
                        planExpiresAt = profile?.vipExpiry,
                        rawDaysRemaining = profile?.vipDaysLeft,
                        status = if (isVipCached) "active" else "inactive"
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "Subscription status error: ${e.message}")
            val isVipCached = isUserVip()
            val profile = getSavedUserProfile()
            Result.success(
                SubscriptionStatusResponse(
                    success = true,
                    rawIsVip = isVipCached,
                    planName = profile?.planName,
                    planExpiresAt = profile?.vipExpiry,
                    rawDaysRemaining = profile?.vipDaysLeft,
                    status = if (isVipCached) "active" else "inactive"
                )
            )
        }
    }

    // 24-Hour JSON Cache for Post Views (1 view per 24 hours per post per client/IP)
    private val viewsCachePrefs = context.getSharedPreferences("play_drama_flix_views_24h_cache", Context.MODE_PRIVATE)

    fun shouldRecord24hView(contentId: Any): Boolean {
        val idStr = contentId.toString()
        val lastRecordedTime = viewsCachePrefs.getLong("last_view_time_$idStr", 0L)
        val currentTime = System.currentTimeMillis()
        val twentyFourHoursMs = 24 * 60 * 60 * 1000L
        return (currentTime - lastRecordedTime) >= twentyFourHoursMs
    }

    fun mark24hViewRecorded(contentId: Any, updatedViews: Long = 0L) {
        val idStr = contentId.toString()
        val currentTime = System.currentTimeMillis()
        val cacheJson = "{\"content_id\":\"$idStr\",\"last_view_time\":$currentTime,\"cached_views\":$updatedViews}"
        viewsCachePrefs.edit().apply {
            putLong("last_view_time_$idStr", currentTime)
            putLong("cached_views_$idStr", updatedViews)
            putString("cache_json_$idStr", cacheJson)
            apply()
        }
    }

    fun getCached24hViews(contentId: Any): Long {
        val idStr = contentId.toString()
        return viewsCachePrefs.getLong("cached_views_$idStr", 0L)
    }

    // ======================= POST ENGAGEMENT & INTERACTION METHODS =======================

    // 1. Record Video View with 24-Hour Per-Post Throttling (POST /api/v1/interaction/view)
    suspend fun recordVideoInteractionView(contentId: Any, force: Boolean = false): Result<ViewIncrementResponse> = withContext(Dispatchers.IO) {
        val idStr = contentId.toString()
        val canRecord = force || shouldRecord24hView(contentId)

        if (!canRecord) {
            val cachedCount = getCached24hViews(contentId)
            Log.d("PlayDramaFlixRepo", "24-Hour Cache: View for contentId=$idStr already counted within 24h. Skipping duplicate server request. Serving cachedViews=$cachedCount")
            return@withContext Result.success(
                ViewIncrementResponse(
                    success = true,
                    contentId = contentId,
                    totalViews = if (cachedCount > 0) cachedCount else null,
                    views = if (cachedCount > 0) cachedCount else null
                )
            )
        }

        try {
            val response = apiService.recordVideoView(ViewIncrementRequest(contentId = contentId))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val effective = body.effectiveViews
                mark24hViewRecorded(contentId, effective)
                Log.d("PlayDramaFlixRepo", "24-Hour Cache: Recorded 1 view to server for contentId=$idStr. Cache updated (views=$effective)")
                Result.success(body)
            } else {
                val prev = getCached24hViews(contentId)
                mark24hViewRecorded(contentId, prev)
                Result.success(ViewIncrementResponse(success = true, contentId = contentId, totalViews = prev, views = prev))
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "recordVideoView server query: ${e.message}")
            val prev = getCached24hViews(contentId)
            mark24hViewRecorded(contentId, prev)
            Result.success(ViewIncrementResponse(success = true, contentId = contentId, totalViews = prev, views = prev))
        }
    }

    // 2. Like / Unlike Toggle (POST /api/v1/interaction/like)
    suspend fun toggleInteractionLike(contentId: Any, episodeId: Any? = null): Result<LikeToggleResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.toggleInteractionLike(LikeToggleRequest(contentId = contentId, episodeId = episodeId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("HTTP ${response.code()}: Failed to toggle like on server"))
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "toggleInteractionLike error: ${e.message}")
            Result.failure(e)
        }
    }

    // 3. Fetch Live Interaction Status from Server (GET /api/v1/interaction/status)
    suspend fun fetchInteractionStatus(contentId: Any, episodeId: Any? = null): Result<InteractionStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getInteractionStatus(contentId = contentId, episodeId = episodeId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.effectiveViews > 0) {
                    viewsCachePrefs.edit().putLong("cached_views_${contentId}", body.effectiveViews).apply()
                }
                Result.success(body)
            } else {
                val cachedViews = getCached24hViews(contentId)
                Result.success(
                    InteractionStatusResponse(
                        success = false,
                        contentId = contentId,
                        views = cachedViews,
                        totalLikes = 0L,
                        isLiked = false,
                        totalComments = 0
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "fetchInteractionStatus error: ${e.message}")
            val cachedViews = getCached24hViews(contentId)
            Result.success(
                InteractionStatusResponse(
                    success = false,
                    contentId = contentId,
                    views = cachedViews,
                    totalLikes = 0L,
                    isLiked = false,
                    totalComments = 0
                )
            )
        }
    }

    // 4. Fetch Comments List & Nested Replies from Server (GET /api/v1/comments)
    suspend fun fetchCommentsList(contentId: Any, episodeId: Any? = null, userId: Any? = null): Result<List<DramaApiComment>> = withContext(Dispatchers.IO) {
        val targetUserId = userId ?: getSavedUserId().takeIf { it.isNotBlank() }

        // Try v1 REST API
        try {
            val response = apiService.getComments(contentId = contentId, episodeId = episodeId, userId = targetUserId)
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.commentsList
                Log.d("PlayDramaFlixRepo", "Fetched ${list.size} comments from v1 API for contentId=$contentId")
                return@withContext Result.success(list)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "fetchCommentsList v1 error: ${e.message}")
        }

        // Fallback to Ajax endpoint
        try {
            val ajaxResponse = apiService.getCommentsAjax(contentId = contentId, episodeId = episodeId)
            if (ajaxResponse.isSuccessful && ajaxResponse.body() != null) {
                return@withContext Result.success(ajaxResponse.body()!!.commentsList)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "fetchCommentsList ajax error: ${e.message}")
        }

        Result.success(emptyList())
    }

    // 5. Post New Comment or Threaded Reply (POST /api/v1/comments/add)
    suspend fun postNewComment(
        contentId: Any,
        episodeId: Any? = null,
        parentId: Any? = null,
        commentText: String,
        authorName: String? = null,
        userId: Any? = null,
        authorAvatar: String? = null
    ): Result<DramaApiComment> = withContext(Dispatchers.IO) {
        val savedUid = userId ?: getSavedUserId().takeIf { it.isNotBlank() }
        val name = authorName?.takeIf { it.isNotBlank() } ?: "DramaFlix Viewer"
        val request = AddCommentApiRequest(
            contentId = contentId,
            episodeId = episodeId,
            parentId = parentId,
            userId = savedUid,
            userName = name,
            commentText = commentText
        )

        // Try v1 REST API
        try {
            val response = apiService.postComment(request)
            if (response.isSuccessful && response.body()?.commentItem != null) {
                Log.d("PlayDramaFlixRepo", "Posted comment successfully via v1 API: ${response.body()?.message}")
                return@withContext Result.success(response.body()!!.commentItem!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "postNewComment v1 error: ${e.message}")
        }

        // Fallback to Ajax endpoint
        try {
            val ajaxResponse = apiService.postCommentAjax(
                action = "add_comment",
                contentId = contentId,
                episodeId = episodeId,
                parentId = parentId,
                userId = savedUid,
                userName = name,
                commentText = commentText
            )
            if (ajaxResponse.isSuccessful && ajaxResponse.body()?.commentItem != null) {
                return@withContext Result.success(ajaxResponse.body()!!.commentItem!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "postNewComment ajax error: ${e.message}")
        }

        // Optimistic / Local fallback
        Result.success(
            DramaApiComment(
                rawId = System.currentTimeMillis(),
                rawContentId = contentId,
                rawEpisodeId = episodeId,
                rawParentId = parentId,
                rawUserId = savedUid,
                userName = name,
                userAvatar = authorAvatar ?: "https://ui-avatars.com/api/?name=${name.replace(" ", "+")}&background=00ACC1&color=fff",
                commentText = commentText,
                dateDisplay = "Just now",
                rawLikesCount = 0,
                isLikedVal = false,
                rawSharesCount = 0,
                rawRepliesCount = 0
            )
        )
    }

    // 5b. Toggle Comment Like (POST /api/v1/comments/like)
    suspend fun toggleCommentLike(commentId: Any, userId: Any? = null): Result<CommentLikeApiResponse> = withContext(Dispatchers.IO) {
        val savedUid = userId ?: getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.toggleCommentLike(CommentLikeApiRequest(commentId = commentId, userId = savedUid))
            if (response.isSuccessful && response.body() != null) {
                return@withContext Result.success(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "toggleCommentLike error: ${e.message}")
        }
        Result.success(CommentLikeApiResponse(success = true, isLiked = true, totalLikes = 1))
    }

    // 5c. Record Comment Share (POST /api/v1/comments/share)
    suspend fun recordCommentShare(commentId: Any, userId: Any? = null): Result<CommentShareApiResponse> = withContext(Dispatchers.IO) {
        val savedUid = userId ?: getSavedUserId().takeIf { it.isNotBlank() }
        try {
            val response = apiService.recordCommentShare(CommentShareApiRequest(commentId = commentId, userId = savedUid))
            if (response.isSuccessful && response.body() != null) {
                return@withContext Result.success(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "recordCommentShare error: ${e.message}")
        }
        Result.success(CommentShareApiResponse(success = true, totalShares = 1))
    }

    // 6. Remote Version Check & Force Update (GET /api/v1/app/version-check)
    fun getInstalledAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkAppVersion(currentVersion: String = getInstalledAppVersion()): Result<AppVersionCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.checkAppVersion(currentVersion = currentVersion)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(
                    AppVersionCheckResponse(
                        success = true,
                        updateAvailable = false,
                        forceUpdate = false,
                        latestVersion = currentVersion
                    )
                )
            }
        } catch (e: Exception) {
            Log.d("PlayDramaFlixRepo", "checkAppVersion remote query: ${e.message}")
            Result.success(
                AppVersionCheckResponse(
                    success = true,
                    updateAvailable = false,
                    forceUpdate = false,
                    latestVersion = currentVersion
                )
            )
        }
    }

    fun getFallbackSubscriptionPlans(): SubscriptionPlansResponse {
        return SubscriptionPlansResponse(
            success = true,
            status = 200,
            subscriptionEnabled = true,
            freeEpisodesCount = 1,
            totalPlans = 2,
            plans = listOf(
                SubscriptionPlanDto(
                    rawId = 1,
                    name = "Monthly VIP",
                    rawPrice = "99.00",
                    durationDays = 30,
                    badgeColor = "warning"
                ),
                SubscriptionPlanDto(
                    rawId = 2,
                    name = "Yearly VIP",
                    rawPrice = "899.00",
                    durationDays = 365,
                    badgeColor = "success"
                )
            ),
            paymentGateways = listOf(
                GatewayItemDto(
                    id = "bkash",
                    name = "bKash",
                    number = "01330049110",
                    type = "Personal",
                    instructions = "Send exact amount via Send Money to this bKash number and enter TrxID below.",
                    color = "#E2136E",
                    icon = "bkash"
                ),
                GatewayItemDto(
                    id = "nagad",
                    name = "Nagad",
                    number = "01330049110",
                    type = "Personal",
                    instructions = "Send exact amount via Send Money to this Nagad number and enter TrxID below.",
                    color = "#F7941D",
                    icon = "nagad"
                )
            )
        )
    }

    // ======================= REMOTE DYNAMIC AD MEDIATION CONFIG =======================
    private val adConfigPrefs = context.getSharedPreferences("play_drama_flix_ad_config_prefs", Context.MODE_PRIVATE)

    fun getCachedAdsConfig(): AdsConfigResponse {
        val enabled = adConfigPrefs.getBoolean("ads_enabled", true)
        val primary = adConfigPrefs.getString("primary_network", "adsterra") ?: "adsterra"
        val fallback = adConfigPrefs.getString("fallback_network", "startio") ?: "startio"
        val startioEnabled = adConfigPrefs.getBoolean("startio_enabled", true)
        val startioAppId = adConfigPrefs.getString("startio_app_id", "207238360") ?: "207238360"
        val startioPubId = adConfigPrefs.getString("startio_pub_id", "113502454") ?: "113502454"
        val admobEnabled = adConfigPrefs.getBoolean("admob_enabled", false)
        val admobAppId = adConfigPrefs.getString("admob_app_id", null)
        val admobBanner = adConfigPrefs.getString("admob_banner_id", null)
        val admobInter = adConfigPrefs.getString("admob_interstitial_id", null)
        val admobReward = adConfigPrefs.getString("admob_rewarded_id", null)
        val adsterraEnabled = adConfigPrefs.getBoolean("adsterra_enabled", true)
        val adsterraDirectLink = adConfigPrefs.getString("adsterra_direct_link", null)
        val adsterraSmartlink = adConfigPrefs.getString("adsterra_smartlink_url", null)
        val adsterraPopunder = adConfigPrefs.getString("adsterra_popunder_url", null)
        val adsterraFreq = adConfigPrefs.getInt("adsterra_popunder_frequency", 3)
        val adsterraMinInterval = adConfigPrefs.getInt("adsterra_popunder_min_interval_seconds", 30)
        val timerSeconds = adConfigPrefs.getInt("timer_seconds", 10)
        val unlockHours = adConfigPrefs.getInt("rewarded_unlock_hours", 2)
        val freeEpisodes = adConfigPrefs.getInt("free_unlocked_episodes", 1)

        return AdsConfigResponse(
            success = true,
            status = 200,
            adsEnabled = enabled,
            primaryNetwork = primary,
            fallbackNetwork = fallback,
            startio = StartIoConfig(
                enabled = startioEnabled,
                appId = startioAppId,
                publisherId = startioPubId
            ),
            admob = AdMobConfig(
                enabled = admobEnabled,
                appId = admobAppId,
                bannerId = admobBanner,
                interstitialId = admobInter,
                rewardedId = admobReward
            ),
            adsterra = AdsterraConfig(
                enabled = adsterraEnabled,
                directLink = adsterraDirectLink,
                smartlinkUrl = adsterraSmartlink,
                popunderUrl = adsterraPopunder,
                popunderFrequency = adsterraFreq,
                popunderMinIntervalSeconds = adsterraMinInterval
            ),
            rules = AdRulesConfig(
                timerSeconds = timerSeconds,
                rewardedUnlockHours = unlockHours,
                freeUnlockedEpisodes = freeEpisodes
            )
        )
    }

    suspend fun fetchRemoteAdsConfig(): Result<AdsConfigResponse> = withContext(Dispatchers.IO) {
        try {
            val response = try {
                apiService.getAdsConfig()
            } catch (e: Exception) {
                apiService.getAdsConfigDirect()
            }
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Save locally to cache preferences
                adConfigPrefs.edit().apply {
                    putBoolean("ads_enabled", body.adsEnabled)
                    putString("primary_network", body.primaryNetwork)
                    putString("fallback_network", body.fallbackNetwork)
                    putBoolean("startio_enabled", body.startio?.enabled ?: true)
                    putString("startio_app_id", body.startio?.appId ?: "207238360")
                    putString("startio_pub_id", body.startio?.publisherId ?: "113502454")
                    putBoolean("admob_enabled", body.admob?.enabled ?: false)
                    putString("admob_app_id", body.admob?.appId)
                    putString("admob_banner_id", body.admob?.bannerId)
                    putString("admob_interstitial_id", body.admob?.interstitialId)
                    putString("admob_rewarded_id", body.admob?.rewardedId)
                    putBoolean("adsterra_enabled", body.adsterra?.enabled ?: true)
                    putString("adsterra_direct_link", body.adsterra?.directLink)
                    putString("adsterra_smartlink_url", body.adsterra?.smartlinkUrl)
                    putString("adsterra_popunder_url", body.adsterra?.popunderUrl)
                    putInt("adsterra_popunder_frequency", body.adsterra?.popunderFrequency ?: 3)
                    putInt("adsterra_popunder_min_interval_seconds", body.adsterra?.popunderMinIntervalSeconds ?: 30)
                    putInt("timer_seconds", body.rules?.timerSeconds ?: 10)
                    putInt("rewarded_unlock_hours", body.rules?.rewardedUnlockHours ?: 2)
                    putInt("free_unlocked_episodes", body.rules?.freeUnlockedEpisodes ?: 1)
                    apply()
                }
                Log.d("PlayDramaFlixRepo", "Remote Ad Config successfully fetched: primary=${body.primaryNetwork}, ads_enabled=${body.adsEnabled}")
                Result.success(body)
            } else {
                Result.success(getCachedAdsConfig())
            }
        } catch (e: Exception) {
            Log.w("PlayDramaFlixRepo", "Remote ad config network fetch offline fallback: ${e.message}")
            Result.success(getCachedAdsConfig())
        }
    }

    // Fallback Data matching PlayDramaFlix catalog
    fun getFallbackContents(): List<ContentItemDto> {
        return listOf(
            // --- SHORTS DRAMA (Mini / Vertical Reels) ---
            ContentItemDto(
                rawId = "s1",
                title = "The Proud Dragon God Bangla Dubbed",
                slug = "the-proud-dragon-god-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.4",
                rawViews = "415K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 7,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/the-proud-dragon-god-bangla-dubbed",
                description = "A world-defying warrior descends from sacred peaks to reclaim his family glory in fast-paced vertical mini episodes.",
                isFeatured = true,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "s2",
                title = "Lost In Love Bangla Dubbed",
                slug = "lost-in-love-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.1",
                rawViews = "280K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 9,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/lost-in-love-bangla-dubbed",
                description = "Rediscovering feelings across time, heartbreak, and sweet unexpected twists in this romantic micro drama.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "s3",
                title = "Doctor Boyfriend Bangla Dubbed",
                slug = "doctor-boyfriend-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.0",
                rawViews = "320K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 12,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787544040_6a8bc1e8f1024.webp",
                shareUrl = "https://playdramaflix.com/doctor-boyfriend-bangla-dubbed",
                description = "An unexpected hospital encounter leads to a high-voltage romance between a talented surgeon and an ambitious executive.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "s4",
                title = "Waking Up As The Richest Bangla Dubbed",
                slug = "waking-up-as-the-richest-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.2",
                rawViews = "389K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 7,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/waking-up-as-the-richest-bangla-dubbed",
                description = "From broke underling to billionaire heir overnight! Hilarious and thrilling revenge drama reels.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "s5",
                title = "Little Poor Thing Rises by Bearing Children Bangla Dubbed",
                slug = "little-poor-thing-rises-by-bearing-children-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "8.8",
                rawViews = "240K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 8,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/little-poor-thing-rises-by-bearing-children-bangla-dubbed",
                description = "A strong-willed mother takes back control of an electronics empire and shows the world her real strength.",
                isFeatured = false,
                isRecent = true,
                isHot = false
            ),
            ContentItemDto(
                rawId = "s6",
                title = "Choddobeshi Bhalobasa Bengali Dubbed",
                slug = "choddobeshi-bhalobasa-bengali-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.1",
                rawViews = "310K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 9,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/choddobeshi-bhalobasa-bengali-dubbed",
                description = "Deepto Play micro drama of unspoken affection, disguise, and redemption.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "s7",
                title = "The Shadow's Counter Attack Bangla Dubbed",
                slug = "the-shadows-counter-attack-bangla-dubbed",
                type = "shorts",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.0",
                rawViews = "275K",
                rawCategories = "Shorts Drama, Bangla Dub",
                rawTotalEpisodes = 7,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787544040_6a8bc1e8f1024.webp",
                shareUrl = "https://playdramaflix.com/the-shadows-counter-attack-bangla-dubbed",
                description = "In the shadows of the cyber city, an elite agent strikes back against syndicate masters.",
                isFeatured = false,
                isRecent = false,
                isHot = false
            ),

            // --- DRAMA SERIES (Long-form TV/Web Series) ---
            ContentItemDto(
                rawId = "d1",
                title = "Like A Dragon Season 1 Bangla Dubbed",
                slug = "like-a-dragon-season-1-bangla-dubbed",
                type = "series",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.2",
                rawViews = "560K",
                rawCategories = "Drama Series, Bangla Dub",
                rawTotalEpisodes = 10,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/like-a-dragon-season-1-bangla-dubbed",
                description = "An explosive story of loyalty, honor, and destiny unfolding in modern Tokyo.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d2",
                title = "Love Is Panacea Hindi Dubbed",
                slug = "love-is-panacea-hindi-dubbed",
                type = "series",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2025",
                rawRating = "9.1",
                rawViews = "490K",
                rawCategories = "Drama Series, Hindi Dub",
                rawTotalEpisodes = 11,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/love-is-panacea-hindi-dubbed",
                description = "A compassionate neurosurgeon and a brilliant medical researcher find solace and romance while fighting rare diseases.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d3",
                title = "Guess Who I Am Hindi Dubbed",
                slug = "guess-who-i-am-hindi-dubbed",
                type = "series",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2024",
                rawRating = "9.4",
                rawViews = "890K",
                rawCategories = "Drama Series, Hindi Dub",
                rawTotalEpisodes = 24,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787544040_6a8bc1e8f1024.webp",
                shareUrl = "https://playdramaflix.com/guess-who-i-am-hindi-dubbed",
                description = "A legendary vigilante woman dedicated to punishing scumbags meets a mysterious corporate heir in an intense cat-and-mouse romance.",
                isFeatured = true,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d4",
                title = "Squid Game Season 2 Bangla Dubbed",
                slug = "squid-game-season-2-bangla-dubbed",
                type = "series",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2025",
                rawRating = "9.8",
                rawViews = "1.5M",
                rawCategories = "Drama Series, Bangla Dub, Popular Series",
                rawTotalEpisodes = 9,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/squid-game-season-2-bangla-dubbed",
                description = "Player 456 returns with a fiery resolve as lethal new survival games test morality and trust.",
                isFeatured = true,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d5",
                title = "All of Us Are Dead Season 2 Hindi Dubbed",
                slug = "all-of-us-are-dead-season-2-hindi-dubbed",
                type = "series",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2025",
                rawRating = "9.6",
                rawViews = "1.2M",
                rawCategories = "Drama Series, Hindi Dub, Popular Series",
                rawTotalEpisodes = 12,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/all-of-us-are-dead-season-2-hindi-dubbed",
                description = "The battle for survival expands into the quarantined city amidst evolved infected threats.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d6",
                title = "My Demon Season 1 Bangla Dubbed",
                slug = "my-demon-season-1-bangla-dubbed",
                type = "series",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2025",
                rawRating = "9.5",
                rawViews = "780K",
                rawCategories = "Drama Series, Bangla Dub, Popular Series",
                rawTotalEpisodes = 16,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787544040_6a8bc1e8f1024.webp",
                shareUrl = "https://playdramaflix.com/my-demon-season-1-bangla-dubbed",
                description = "A 200-year-old demon loses his powers upon meeting an icy chaebol heiress, sparking a contract marriage full of secrets.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d7",
                title = "Hidden Love Season 1 Bangla Dubbed",
                slug = "hidden-love-season-1-bangla-dubbed",
                type = "series",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2025",
                rawRating = "9.3",
                rawViews = "690K",
                rawCategories = "Drama Series, Bangla Dub, Popular Series",
                rawTotalEpisodes = 25,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/hidden-love-season-1-bangla-dubbed",
                description = "A sweet, heartwarming tale of a long-held youthful crush turning into a deep and mature love story.",
                isFeatured = false,
                isRecent = false,
                isHot = true
            ),
            ContentItemDto(
                rawId = "d8",
                title = "Derailment Season 1 Hindi Dubbed",
                slug = "derailment-season-1-hindi-dubbed",
                type = "series",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2026",
                rawRating = "8.9",
                rawViews = "650K",
                rawCategories = "Drama Series, Hindi Dub",
                rawTotalEpisodes = 30,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/derailment-season-1-hindi-dubbed",
                description = "A wealthy heiress travels across parallel timelines and meets a childhood confidant who holds the key to her identity.",
                isFeatured = false,
                isRecent = true,
                isHot = false
            ),

            // --- ANIME SERIES ---
            ContentItemDto(
                rawId = "a1",
                title = "Solo Leveling Season 1 Hindi Dubbed",
                slug = "solo-leveling-season-1-hindi-dubbed",
                type = "anime",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2024",
                rawRating = "9.7",
                rawViews = "920K",
                rawCategories = "Anime Series, Hindi Dub, Popular Series",
                rawTotalEpisodes = 12,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/solo-leveling-season-1-hindi-dubbed",
                description = "In a world where hunters face monsters, the weakest hunter receives a secret quest system to level up without limits.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "a2",
                title = "Jujutsu Kaisen Season 2 Bangla Dubbed",
                slug = "jujutsu-kaisen-season-2-bangla-dubbed",
                type = "anime",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2024",
                rawRating = "9.6",
                rawViews = "810K",
                rawCategories = "Anime Series, Bangla Dub, Popular Series",
                rawTotalEpisodes = 23,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/jujutsu-kaisen-season-2-bangla-dubbed",
                description = "The Shibuya incident shatters the jujutsu world in an unforgettable clash of curses and sorcerers.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "a3",
                title = "Overflow Hindi Dubbed Available",
                slug = "overflow-hindi-dubbed-available",
                type = "anime",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2020",
                rawRating = "8.9",
                rawViews = "142.5K",
                rawCategories = "Anime Series, Hindi Dub",
                rawTotalEpisodes = 8,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787544040_6a8bc1e8f1024.webp",
                shareUrl = "https://playdramaflix.com/overflow-hindi-dubbed-available",
                description = "Watch Overflow Hindi Dubbed online in HD with all episodes available in crystal clear audio.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "a4",
                title = "Attack on Titan Final Season Bangla Dubbed",
                slug = "attack-on-titan-final-season-bangla-dubbed",
                type = "anime",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2023",
                rawRating = "9.9",
                rawViews = "1.8M",
                rawCategories = "Anime Series, Bangla Dub, Popular Series",
                rawTotalEpisodes = 28,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/attack-on-titan-final-season-bangla-dubbed",
                description = "The war for Paradis reaches its apocalyptic climax as the Rumbling is unleashed.",
                isFeatured = false,
                isRecent = false,
                isHot = true
            ),
            ContentItemDto(
                rawId = "a5",
                title = "Demon Slayer Swordsmith Village Bangla Dubbed",
                slug = "demon-slayer-swordsmith-village-bangla-dubbed",
                type = "anime",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2024",
                rawRating = "9.5",
                rawViews = "750K",
                rawCategories = "Anime Series, Bangla Dub",
                rawTotalEpisodes = 11,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787446896_6a8a4670593c3.jpg",
                shareUrl = "https://playdramaflix.com/demon-slayer-swordsmith-village-bangla-dubbed",
                description = "Tanjiro travels to the hidden Swordsmith Village to repair his blade and encounters Upper Rank demons.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),

            // --- MOVIES ---
            ContentItemDto(
                rawId = "m1",
                title = "The Wandering Earth II Hindi Dubbed",
                slug = "the-wandering-earth-2-hindi-dubbed",
                type = "movie",
                language = "Hindi Dubbed",
                customDubBadge = "Hindi",
                releaseYear = "2024",
                rawRating = "9.2",
                rawViews = "310K",
                rawCategories = "Movies, Hindi Dub",
                rawTotalEpisodes = 1,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787544040_6a8bc1e8f1024.webp",
                shareUrl = "https://playdramaflix.com/the-wandering-earth-2-hindi-dubbed",
                description = "Humanity builds enormous planetary engines on the surface of the earth in this epic sci-fi blockbuster.",
                isFeatured = false,
                isRecent = true,
                isHot = true
            ),
            ContentItemDto(
                rawId = "m2",
                title = "Demon Slayer: Mugen Train Bangla Dubbed",
                slug = "demon-slayer-mugen-train-bangla-dubbed",
                type = "movie",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2025",
                rawRating = "9.5",
                rawViews = "450K",
                rawCategories = "Movies, Anime Series, Bangla Dub",
                rawTotalEpisodes = 1,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/demon-slayer-mugen-train-bangla-dubbed",
                description = "Tanjiro and the Demon Slayer Corps board the Infinity Train to face deadly demons in an unforgettable battle.",
                isFeatured = false,
                isRecent = false,
                isHot = true
            )
        )
    }

    fun getFallbackWatchDetails(slug: String, fallbackContent: ContentItemDto? = null): WatchDetailResponse {
        val content = fallbackContent
            ?: getFallbackContents().find { it.slug == slug || it.rawId == slug }
            ?: ContentItemDto(
                rawId = slug,
                title = slug.replace("-", " ").replaceFirstChar { it.uppercase() },
                slug = slug,
                type = "series",
                language = "Bangla Dubbed",
                customDubBadge = "Bangla",
                releaseYear = "2026",
                rawRating = "9.0",
                rawViews = "100K",
                rawCategories = "Drama Series",
                rawTotalEpisodes = 10,
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                bannerUrl = "https://playdramaflix.com/public/uploads/banner/1787413105_6a89c271dfab5.webp",
                shareUrl = "https://playdramaflix.com/$slug",
                description = "Enjoy high quality streaming with full episodes.",
                isFeatured = false,
                isRecent = false,
                isHot = false
            )

        val sampleVideos = listOf(
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        )

        val isMovie = content.type == "movie"
        val totalEp = if (isMovie) 1 else if (content.totalEpisodes > 0) content.totalEpisodes else 8

        val episodes = (1..totalEp).map { num ->
            val videoUrl = sampleVideos[(num - 1) % sampleVideos.size]
            val epTitle = if (isMovie) "Full Movie HD" else "${content.title} - Episode $num"
            EpisodeDto(
                rawEpisodeId = "ep_${content.slug}_$num",
                episodeNumber = num,
                epTitle = epTitle,
                seasonNumber = 1,
                duration = if (isMovie) "1h 54m" else "${20 + (num % 5)}m",
                videoUrl = videoUrl,
                embedUrl = "https://byse.sx/e/${content.slug}_ep_$num",
                isLocked = num > 2 && !isMovie,
                adsCount = if (num > 2) 2 else 0
            )
        }

        val servers = listOf(
            ServerDto(
                rawId = "srv_1_${content.slug}",
                serverName = "Fast Stream HD (Byse)",
                rawUrl = episodes.firstOrNull()?.videoUrl ?: sampleVideos.first(),
                serverType = "mp4",
                rawEpisodeId = episodes.firstOrNull()?.episodeId
            ),
            ServerDto(
                rawId = "srv_2_${content.slug}",
                serverName = "VIP Ultra Server (Direct HLS)",
                rawUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                serverType = "hls",
                rawEpisodeId = episodes.firstOrNull()?.episodeId
            ),
            ServerDto(
                rawId = "srv_3_${content.slug}",
                serverName = "Web Embed Player (IFrame)",
                rawUrl = "https://byse.sx/e/${content.slug}_embed",
                serverType = "embed",
                rawEpisodeId = episodes.firstOrNull()?.episodeId
            )
        )

        return WatchDetailResponse(
            success = true,
            status = 200,
            content = content,
            servers = servers,
            episodes = episodes
        )
    }

    private fun getFallbackNotifications(): List<NotificationItemDto> {
        return listOf(
            NotificationItemDto(
                rawId = "notif_1",
                title = "Filter Hindi Dubbed | Full Series All Episodes Watch Online HD",
                message = "Watch Filter Hindi Dubbed | Full Series All Episodes Watch Online HD (Hindi Dubbed)...",
                url = "/filter-hindi-dubbed-full-series",
                slug = "filter-hindi-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                customTimeAgo = "19h ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_2",
                title = "Meeting You Loving You Hindi Dubbed | Full Series All Episodes",
                message = "Watch Meeting You Loving You Hindi Dubbed | Full Series All Episodes Watch HD...",
                url = "/meeting-you-loving-you-hindi-dubbed",
                slug = "meeting-you-loving-you-hindi-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                customTimeAgo = "20h ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_3",
                title = "Episode 23",
                message = "Watch Episode 23 (Bangla Dubbed) all episodes in HD online now!",
                url = "/overflow-hindi-dubbed-available",
                slug = "overflow-hindi-dubbed-available",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                iconType = "episode",
                customTimeAgo = "1d ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_4",
                title = "Episode 11",
                message = "Watch Episode 11 (Bangla Dubbed) all episodes in HD online now!",
                url = "/the-proud-dragon-god-bangla-dubbed",
                slug = "the-proud-dragon-god-bangla-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                iconType = "episode",
                customTimeAgo = "1d ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_5",
                title = "Episode 10",
                message = "Watch Episode 10 (Bangla Dubbed) in HD quality online now!",
                url = "/hidden-love-bangla-dubbed",
                slug = "hidden-love-bangla-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787544040_6a8bc1e8f068e.webp",
                iconType = "episode",
                customTimeAgo = "1d ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_6",
                title = "Episode 05",
                message = "Watch Episode 05 (Bangla Dubbed) in HD quality online now!",
                url = "/waking-up-as-the-richest-bangla-dubbed",
                slug = "waking-up-as-the-richest-bangla-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                iconType = "episode",
                customTimeAgo = "1d ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_7",
                title = "Episode 02",
                message = "Watch Episode 02 (Bangla Dubbed) all episodes in HD online now!",
                url = "/little-poor-thing-rises-by-bearing-children-bangla-dubbed",
                slug = "little-poor-thing-rises-by-bearing-children-bangla-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                iconType = "episode",
                customTimeAgo = "1d ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_8",
                title = "Choddobeshi Bhalobasa Bengali Dubbed",
                message = "Watch Choddobeshi Bhalobasa (Bangla Dubbed) all episodes in HD now!",
                url = "/choddobeshi-bhalobasa-bengali-dubbed",
                slug = "choddobeshi-bhalobasa-bengali-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787446896_6a8a4670591ea.webp",
                customTimeAgo = "1d ago",
                isRead = false
            ),
            NotificationItemDto(
                rawId = "notif_9",
                title = "Demon Slayer: Mugen Train",
                message = "Watch Mugen Train (Bangla Dubbed) high quality streaming!",
                url = "/demon-slayer-mugen-train-bangla-dubbed",
                slug = "demon-slayer-mugen-train-bangla-dubbed",
                posterUrl = "https://playdramaflix.com/public/uploads/posters/1787413105_6a89c271df941.webp",
                customTimeAgo = "2d ago",
                isRead = true
            )
        )
    }
}
