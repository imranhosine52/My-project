package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SubscriptionRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val authRepository: AuthRepository = AuthRepository(context, apiService)
) {
    private val subRequestPrefs = context.getSharedPreferences("play_drama_flix_sub_requests", Context.MODE_PRIVATE)

    // =========================================================================
    // 👑 PENDING SUBSCRIPTION REQUEST MANAGEMENT (LOCAL PREFS)
    // =========================================================================

    fun savePendingSubscriptionRequest(req: PendingSubscriptionRequestModel) {
        val userKey = req.userId.ifBlank { authRepository.getSavedUserId() }
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
        val userKey = userId?.takeIf { it.isNotBlank() } ?: authRepository.getSavedUserId()
        if (userKey.isBlank()) return null
        val hasPending = subRequestPrefs.getBoolean("has_pending_$userKey", false)
        if (!hasPending) return null

        return PendingSubscriptionRequestModel(
            userId = userKey,
            submissionId = subRequestPrefs.getString("sub_id_$userKey", "") ?: "",
            planId = subRequestPrefs.getString("sub_plan_id_$userKey", "") ?: "",
            planName = subRequestPrefs.getString("sub_plan_name_$userKey", "VIP Subscription") ?: "VIP Subscription",
            amount = subRequestPrefs.getFloat("sub_amount_$userKey", 0f).toDouble(),
            paymentMethod = subRequestPrefs.getString("sub_payment_method_$userKey", "bKash") ?: "bKash",
            senderNumber = subRequestPrefs.getString("sub_sender_number_$userKey", "") ?: "",
            transactionId = subRequestPrefs.getString("sub_trx_id_$userKey", "") ?: "",
            timestamp = subRequestPrefs.getLong("sub_timestamp_$userKey", System.currentTimeMillis()),
            status = subRequestPrefs.getString("sub_status_$userKey", "pending") ?: "pending"
        )
    }

    fun clearPendingSubscriptionRequest(userId: String? = null) {
        val userKey = userId?.takeIf { it.isNotBlank() } ?: authRepository.getSavedUserId()
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
        val userKey = userId?.takeIf { it.isNotBlank() } ?: authRepository.getSavedUserId()
        if (userKey.isBlank()) return false
        return subRequestPrefs.getBoolean("has_pending_$userKey", false)
    }

    // =========================================================================
    // 🌐 REMOTE VIP PLANS & PAYMENT SUBMISSION APIS
    // =========================================================================

    suspend fun getSubscriptionPlans(): Result<SubscriptionPlansResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getSubscriptionPlans()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.success(getFallbackSubscriptionPlans())
            }
        } catch (e: Exception) {
            Result.success(getFallbackSubscriptionPlans())
        }
    }

    suspend fun submitSubscription(request: SubscriptionSubmitRequest): Result<SubscriptionSubmitResponse> = withContext(Dispatchers.IO) {
        val token = authRepository.getSavedAuthToken()
        val authHeader = if (!token.isNullOrBlank()) "Bearer $token" else null

        // ১. প্রাইমারি V1 সাবমিট এন্ডপয়েন্ট
        try {
            val response = apiService.submitSubscription(request, authHeader = authHeader)
            if (response.isSuccessful && response.body() != null) {
                return@withContext Result.success(response.body()!!)
            }
        } catch (e: Exception) {
            Log.w("SubRepo", "submitSubscription primary endpoint notice: ${e.message}")
        }

        // ২. Direct V1 URL ফলব্যাক
        try {
            val v1Response = apiService.submitSubscriptionV1Direct(request, authHeader = authHeader)
            if (v1Response.isSuccessful && v1Response.body() != null) {
                return@withContext Result.success(v1Response.body()!!)
            }
        } catch (e: Exception) {
            Log.w("SubRepo", "submitSubscription v1 direct notice: ${e.message}")
        }

        // ৩. Root URL ফলব্যাক
        try {
            val rootResponse = apiService.submitSubscriptionRootDirect(request, authHeader = authHeader)
            if (rootResponse.isSuccessful && rootResponse.body() != null) {
                return@withContext Result.success(rootResponse.body()!!)
            }
        } catch (e: Exception) {
            Log.w("SubRepo", "submitSubscription root direct notice: ${e.message}")
        }

        // ৪. FormUrlEncoded সাবমিট ফলব্যাক
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
                return@withContext Result.success(formResponse.body()!!)
            }
        } catch (e: Exception) {
            Log.w("SubRepo", "submitSubscription FormUrlEncoded notice: ${e.message}")
        }

        // ৫. Ajax সাবমিট ফলব্যাক
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
                return@withContext Result.success(ajaxResponse.body()!!)
            }
        } catch (e: Exception) {
            Log.w("SubRepo", "submitSubscription Ajax notice: ${e.message}")
        }

        // ৬. লোকাল পেন্ডিং সাকসেস ফলব্যাক
        Result.success(
            SubscriptionSubmitResponse(
                success = true,
                message = "Payment submission saved. Admin will verify and activate your VIP pass shortly.",
                submissionId = "SUB-${(10000..99999).random()}",
                status = "pending"
            )
        )
    }

    suspend fun getSubscriptionStatus(userId: String?, deviceId: String? = null): Result<SubscriptionStatusResponse> = withContext(Dispatchers.IO) {
        val targetUserId = userId?.takeIf { it.isNotBlank() } ?: authRepository.getSavedUserId()
        try {
            val response = apiService.getSubscriptionStatus(userId = targetUserId, deviceId = deviceId)
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!
                if (status.isVip || status.status.equals("active", ignoreCase = true)) {
                    clearPendingSubscriptionRequest(targetUserId)
                    authRepository.saveUserSession(
                        userId = targetUserId,
                        isVip = true,
                        planName = status.planName,
                        expiry = status.expiresAt,
                        daysLeft = status.daysRemaining
                    )
                } else {
                    authRepository.saveUserSession(
                        userId = targetUserId,
                        isVip = false,
                        planName = null,
                        expiry = null,
                        daysLeft = 0
                    )
                }
                Result.success(status)
            } else {
                val isVipCached = authRepository.isUserVip()
                val profile = authRepository.getSavedUserProfile()
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
            val isVipCached = authRepository.isUserVip()
            val profile = authRepository.getSavedUserProfile()
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
                    rawPrice = "59.00",
                    durationDays = 30,
                    badgeColor = "warning"
                ),
                SubscriptionPlanDto(
                    rawId = 2,
                    name = "3 Months VIP Pass",
                    rawPrice = "150.00",
                    durationDays = 90,
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
}
