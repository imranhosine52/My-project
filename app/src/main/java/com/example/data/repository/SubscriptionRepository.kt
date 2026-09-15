package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.remote.ApiClient
import com.example.data.remote.PlayDramaFlixApiService
import com.example.util.VipStatusNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SubscriptionRepository(
    private val context: Context,
    private val apiService: PlayDramaFlixApiService = ApiClient.apiService,
    private val authRepository: AuthRepository = AuthRepository(context, apiService)
) {
    private val subRequestPrefs = context.getSharedPreferences("play_drama_flix_sub_requests", Context.MODE_PRIVATE)
    private val persistentInvoicePrefs = context.getSharedPreferences("play_drama_flix_local_invoices", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "SubscriptionRepo"
        private const val SUBMIT_API_URL = "https://playdramaflix.com/api/v1/subscription/submit"
    }

    // =========================================================================
    // 🧾 পার্মানেন্ট লোকাল ইনভয়েস স্টোরেজ
    // =========================================================================

    fun saveLocalInvoicePermanently(invoice: InvoiceItemDto) {
        try {
            val currentList = getLocalInvoicesPermanently().toMutableList()
            currentList.removeAll { it.trxId.equals(invoice.trxId, ignoreCase = true) }
            currentList.add(0, invoice)

            val jsonArray = JSONArray()
            currentList.forEach { inv ->
                val obj = JSONObject().apply {
                    put("id", inv.id)
                    put("plan_name", inv.planName)
                    put("amount", inv.displayAmount)
                    put("payment_method", inv.paymentMethod)
                    put("trx_id", inv.trxId)
                    put("status", inv.status)
                    put("date", inv.displayDate)
                }
                jsonArray.put(obj)
            }
            persistentInvoicePrefs.edit().putString("saved_invoices_json", jsonArray.toString()).apply()
            Log.d(TAG, "✓ Invoice saved locally: ${invoice.trxId} [${invoice.status}]")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save invoice locally: ${e.message}")
        }
    }

    fun getLocalInvoicesPermanently(): List<InvoiceItemDto> {
        val jsonStr = persistentInvoicePrefs.getString("saved_invoices_json", "[]") ?: "[]"
        val list = mutableListOf<InvoiceItemDto>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    InvoiceItemDto(
                        rawId = obj.optString("id"),
                        rawPlanName = obj.optString("plan_name"),
                        rawAmount = obj.optString("amount"),
                        rawPaymentMethod = obj.optString("payment_method"),
                        rawTrxId = obj.optString("trx_id"),
                        rawStatus = obj.optString("status", "pending"),
                        date = obj.optString("date", "Recent")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun updateLocalInvoiceStatusPermanently(trxId: String, newStatus: String, planName: String = "VIP Pass") {
        try {
            val currentList = getLocalInvoicesPermanently().map { inv ->
                if (inv.trxId.equals(trxId.trim(), ignoreCase = true)) {
                    inv.copy(rawStatus = newStatus)
                } else inv
            }

            val jsonArray = JSONArray()
            currentList.forEach { inv ->
                val obj = JSONObject().apply {
                    put("id", inv.id)
                    put("plan_name", inv.planName)
                    put("amount", inv.displayAmount)
                    put("payment_method", inv.paymentMethod)
                    put("trx_id", inv.trxId)
                    put("status", inv.status)
                    put("date", inv.displayDate)
                }
                jsonArray.put(obj)
            }
            persistentInvoicePrefs.edit().putString("saved_invoices_json", jsonArray.toString()).apply()

            // নোটিফিকেশন ডিসপ্যাচ
            if (newStatus.equals("approved", true) || newStatus.equals("active", true)) {
                VipStatusNotificationHelper.showVipApprovedNotification(context, planName)
            } else if (newStatus.equals("rejected", true) || newStatus.equals("declined", true) || newStatus.equals("failed", true)) {
                VipStatusNotificationHelper.showVipRejectedNotification(context, "Payment was declined by admin. Please verify your TrxID.")
            }
        } catch (_: Exception) {}
    }

    // =========================================================================
    // 👑 PENDING SUBSCRIPTION REQUEST MANAGEMENT
    // =========================================================================

    fun savePendingSubscriptionRequest(req: PendingSubscriptionRequestModel) {
        val userKey = req.userId.takeIf { it.isNotBlank() } ?: "active_pending_user"
        subRequestPrefs.edit().apply {
            putString("sub_user_id", userKey)
            putString("sub_id", req.submissionId)
            putString("sub_plan_id", req.planId)
            putString("sub_plan_name", req.planName)
            putFloat("sub_amount", req.amount.toFloat())
            putString("sub_payment_method", req.paymentMethod)
            putString("sub_sender_number", req.senderNumber)
            putString("sub_trx_id", req.transactionId)
            putLong("sub_timestamp", req.timestamp)
            putString("sub_status", req.status)
            putBoolean("has_pending_active", true)
            apply()
        }
    }

    fun getPendingSubscriptionRequest(userId: String? = null): PendingSubscriptionRequestModel? {
        val hasPending = subRequestPrefs.getBoolean("has_pending_active", false)
        if (!hasPending) return null

        val trx = subRequestPrefs.getString("sub_trx_id", "") ?: ""
        if (trx.isBlank()) return null

        return PendingSubscriptionRequestModel(
            userId = subRequestPrefs.getString("sub_user_id", "guest") ?: "guest",
            submissionId = subRequestPrefs.getString("sub_id", "INV-${System.currentTimeMillis() % 100000}") ?: "",
            planId = subRequestPrefs.getString("sub_plan_id", "1") ?: "1",
            planName = subRequestPrefs.getString("sub_plan_name", "VIP Subscription") ?: "VIP Subscription",
            amount = subRequestPrefs.getFloat("sub_amount", 59f).toDouble(),
            paymentMethod = subRequestPrefs.getString("sub_payment_method", "bKash") ?: "bKash",
            senderNumber = subRequestPrefs.getString("sub_sender_number", "") ?: "",
            transactionId = trx,
            timestamp = subRequestPrefs.getLong("sub_timestamp", System.currentTimeMillis()),
            status = subRequestPrefs.getString("sub_status", "pending") ?: "pending"
        )
    }

    fun clearPendingSubscriptionRequest(userId: String? = null) {
        subRequestPrefs.edit().apply {
            remove("has_pending_active")
            remove("sub_trx_id")
            remove("sub_status")
            apply()
        }
    }

    fun hasPendingSubscriptionRequest(userId: String? = null): Boolean {
        return subRequestPrefs.getBoolean("has_pending_active", false) &&
                !subRequestPrefs.getString("sub_trx_id", "").isNullOrBlank()
    }

    // =========================================================================
    // 🌐 REMOTE VIP PLANS
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

    // =========================================================================
    // 💳 ১০০% সার্ভার-অথরিটেটিভ পেমেন্ট সাবমিশন (Strictly Validated)
    // =========================================================================

    suspend fun submitSubscription(request: SubscriptionSubmitRequest): Result<SubscriptionSubmitResponse> = withContext(Dispatchers.IO) {
        // ১. ইউজার আইডি নিশ্চিত করা (লগইন ছাড়া সাবমিট ব্লক)
        val uidStr = request.userId.toString().filter { it.isDigit() }.ifBlank {
            authRepository.getSavedUserId().filter { it.isDigit() }
        }
        val uid = uidStr.toIntOrNull() ?: 0

        if (uid <= 0) {
            return@withContext Result.failure(Exception("অনুগ্রহ করে প্রথমে অ্যাকাউন্টে লগইন করুন।"))
        }

        val trx = request.trxId.trim()
        val phone = request.senderNumber?.trim() ?: ""

        if (trx.length < 4 || phone.length < 6) {
            return@withContext Result.failure(Exception("সঠিক সেন্ডার নম্বর এবং Transaction ID (TrxID) প্রদান করুন।"))
        }

        val planIdInt = request.planId.toString().filter { it.isDigit() }.toIntOrNull() ?: 1
        val amountDouble = request.amount ?: 59.0
        val paymentMethod = request.paymentMethod.ifBlank { "bKash" }

        // ২. সার্ভার প্রত্যাশিত JSON বডি তৈরি
        val jsonPayload = JSONObject().apply {
            put("user_id", uid)
            put("plan_id", planIdInt)
            put("payment_method", paymentMethod)
            put("sender_number", phone)
            put("trx_id", trx)
            put("amount", amountDouble)
        }

        var conn: HttpURLConnection? = null
        try {
            val url = URL(SUBMIT_API_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PlayDramaFlix-AndroidApp/1.0")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
                instanceFollowRedirects = true
            }

            // ডেটা পাঠানো
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val inputStream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }

            Log.i(TAG, "Server Submit Response [$responseCode]: $responseText")

            val jsonResponse = try { JSONObject(responseText) } catch (_: Exception) { JSONObject() }
            val isSuccess = jsonResponse.optBoolean("success", false)
            val serverMessage = jsonResponse.optString("message", "পেমেন্ট রিকোয়েস্ট সম্পন্ন হয়েছে।")

            // ৩. সার্ভার অনুমোদন দিলে তবেই Result.success হবে
            if (responseCode in 200..299 && isSuccess) {
                val isAutoApproved = jsonResponse.optBoolean("auto_approved", false)
                val isVip = jsonResponse.optBoolean("is_vip", false)
                val subId = jsonResponse.optString("submission_id", "INV-${System.currentTimeMillis() % 100000}")

                Result.success(
                    SubscriptionSubmitResponse(
                        success = true,
                        autoApproved = isAutoApproved,
                        isVip = isVip,
                        message = serverMessage,
                        submissionId = subId,
                        status = if (isAutoApproved || isVip) "approved" else "pending"
                    )
                )
            } else {
                // ❌ সার্ভার রিজেক্ট করলে আসল এরর মেসেজ পাঠানো হবে
                Result.failure(Exception(serverMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network connection error: ${e.message}", e)
            Result.failure(Exception("সার্ভারের সাথে সংযোগ স্থাপন করা যায়নি। আপনার ইন্টারনেট সংযোগ পরীক্ষা করুন।"))
        } finally {
            conn?.disconnect()
        }
    }

    // =========================================================================
    // 🔍 লাইভ স্ট্যাটাস চেকার ও VIP লাইফসাইকেল সিঙ্ক
    // =========================================================================

    suspend fun getSubscriptionStatus(userId: String?, deviceId: String? = null): Result<SubscriptionStatusResponse> = withContext(Dispatchers.IO) {
        val targetUserId = userId?.takeIf { it.isNotBlank() } ?: authRepository.getSavedUserId()
        val numericUserId = targetUserId.filter { it.isDigit() }

        if (numericUserId.isBlank() || numericUserId == "0") {
            return@withContext Result.success(
                SubscriptionStatusResponse(
                    success = true,
                    rawIsVip = false,
                    planName = null,
                    planExpiresAt = null,
                    rawDaysRemaining = 0,
                    rawStatus = "free"
                )
            )
        }

        val localPending = getPendingSubscriptionRequest()
        val wasVipBefore = authRepository.isUserVip()

        try {
            val response = apiService.getSubscriptionStatus(userId = numericUserId, deviceId = deviceId)
            if (response.isSuccessful && response.body() != null) {
                val status = response.body()!!

                // সার্ভারের ইনভয়েসের সাথে লোকাল পেন্ডিং ট্রানজেকশন মেলানো
                val matchingServerInv = if (localPending != null) {
                    status.allInvoices.find { it.trxId.equals(localPending.transactionId, ignoreCase = true) }
                } else null

                val isServerVip = status.isVip
                val isInvApproved = matchingServerInv?.status == "approved"
                val isInvDeclined = matchingServerInv?.status == "rejected"

                // 👑 ১. সার্ভার এপ্রুভ করলে সাথে সাথে VIP সক্রিয় করা
                if (isServerVip || isInvApproved) {
                    val plan = status.planName ?: localPending?.planName ?: "VIP Pass"
                    if (localPending != null) {
                        updateLocalInvoiceStatusPermanently(localPending.transactionId, "approved", plan)
                        clearPendingSubscriptionRequest(targetUserId)
                    }
                    authRepository.saveUserSession(
                        userId = targetUserId,
                        isVip = true,
                        planName = plan,
                        expiry = status.expiresAt,
                        daysLeft = status.daysRemaining
                    )
                    if (!wasVipBefore || localPending != null) {
                        VipStatusNotificationHelper.showVipApprovedNotification(context, plan)
                    }
                } 
                // ❌ ২. সার্ভার রিজেক্ট করলে পেন্ডিং ক্লিয়ার ও নোটিফিকেশন দেওয়া
                else if (isInvDeclined && localPending != null) {
                    updateLocalInvoiceStatusPermanently(localPending.transactionId, "rejected")
                    clearPendingSubscriptionRequest(targetUserId)
                    VipStatusNotificationHelper.showVipRejectedNotification(context, "Your payment was declined by admin.")
                } 
                // ৩. সার্ভার ভিআইপি না বললে ফ্রি হিসেবে সেট রাখা (Auto-Downgrade)
                else if (!isServerVip && localPending == null) {
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
                // সার্ভার এরর দিলে সেভড প্রোফাইল ব্যবহার
                val profile = authRepository.getSavedUserProfile()
                Result.success(
                    SubscriptionStatusResponse(
                        success = false,
                        rawIsVip = profile?.isVip ?: false,
                        planName = profile?.planName,
                        planExpiresAt = profile?.vipExpiry,
                        rawDaysRemaining = profile?.vipDaysLeft ?: 0,
                        rawStatus = if (profile?.isVip == true) "active" else "free"
                    )
                )
            }
        } catch (e: Exception) {
            val profile = authRepository.getSavedUserProfile()
            Result.success(
                SubscriptionStatusResponse(
                    success = false,
                    rawIsVip = profile?.isVip ?: false,
                    planName = profile?.planName,
                    planExpiresAt = profile?.vipExpiry,
                    rawDaysRemaining = profile?.vipDaysLeft ?: 0,
                    rawStatus = if (profile?.isVip == true) "active" else "free"
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
                SubscriptionPlanDto(rawId = 1, name = "Monthly VIP", rawPrice = "59.00", durationDays = 30, badgeColor = "warning"),
                SubscriptionPlanDto(rawId = 2, name = "3 Months VIP Pass", rawPrice = "150.00", durationDays = 90, badgeColor = "success")
            ),
            paymentGateways = listOf(
                GatewayItemDto(id = "bkash", name = "bKash", number = "01330049110", type = "Personal", color = "#E2136E"),
                GatewayItemDto(id = "nagad", name = "Nagad", number = "01330049110", type = "Personal", color = "#F7941D")
            )
        )
    }
}
