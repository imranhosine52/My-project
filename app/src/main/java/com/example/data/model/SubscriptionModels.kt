package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class SubscriptionPlansResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "subscription_enabled") val subscriptionEnabled: Boolean = true,
    @Json(name = "free_episodes_count") val freeEpisodesCount: Int? = 1,
    @Json(name = "total_plans") val totalPlans: Int? = 0,
    @Json(name = "plans") val plans: List<SubscriptionPlanDto> = emptyList(),
    @Json(name = "payment_gateways") val paymentGateways: List<GatewayItemDto> = emptyList()
) {
    val activePlans: List<SubscriptionPlanDto> get() = plans.filter { it.isActive }
}

@JsonClass(generateAdapter = true)
data class SubscriptionPlanDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "name") val name: String = "VIP Plan",
    @Json(name = "bangla_name") val banglaName: String? = null,
    @Json(name = "price") val rawPrice: Any? = "99.00",
    @Json(name = "original_price") val rawOriginalPrice: Any? = null,
    @Json(name = "duration_days") val durationDays: Int = 30,
    @Json(name = "badge_color") val badgeColor: String? = "warning",
    @Json(name = "badge_text") val badgeText: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "features") val rawFeatures: Any? = null,
    @Json(name = "is_popular") val isPopular: Boolean = false,
    @Json(name = "status") val status: String? = null,
    @Json(name = "active") val rawActive: Any? = true,
    @Json(name = "is_active") val rawIsActive: Any? = null
) {
    val id: String get() = rawId?.toString() ?: "1"
    val planIdInt: Int get() = rawId?.toString()?.toIntOrNull() ?: 1

    val isActive: Boolean
        get() {
            if (status != null && (status.equals("inactive", ignoreCase = true) || status.equals("0"))) return false
            val check = rawIsActive ?: rawActive ?: true
            return when (check) {
                is Boolean -> check
                is Number -> check.toInt() == 1
                is String -> check.equals("1") || check.equals("true", ignoreCase = true)
                else -> true
            }
        }

    val priceDouble: Double
        get() = when (rawPrice) {
            is Number -> rawPrice.toDouble()
            is String -> rawPrice.toDoubleOrNull() ?: 99.0
            else -> 99.0
        }

    val priceFormatted: String
        get() = if (priceDouble % 1.0 == 0.0) priceDouble.toInt().toString() else String.format(Locale.US, "%.2f", priceDouble)

    val originalPriceDouble: Double
        get() = when (rawOriginalPrice) {
            is Number -> rawOriginalPrice.toDouble()
            is String -> rawOriginalPrice.toDoubleOrNull() ?: (priceDouble * 1.5)
            else -> (priceDouble * 1.5)
        }

    val originalPriceFormatted: String
        get() = if (originalPriceDouble % 1.0 == 0.0) originalPriceDouble.toInt().toString() else String.format(Locale.US, "%.2f", originalPriceDouble)

    val discountPercent: Int
        get() {
            val diff = originalPriceDouble - priceDouble
            return if (originalPriceDouble > 0 && diff > 0) ((diff / originalPriceDouble) * 100).toInt() else 0
        }

    val features: List<String>
        get() = when (rawFeatures) {
            is List<*> -> rawFeatures.filterIsInstance<String>()
            is String -> rawFeatures.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> listOf(
                "Ad-Free Ultra Fast Full HD (1080p) Streaming",
                "Unlock All Ongoing & Upcoming Dubbed Episodes",
                "Unlimited Video Downloads for Offline Watching",
                "Dedicated Cloudflare R2 Fast Streaming Nodes"
            )
        }
}

data class PaymentMethodModel(
    val key: String,
    val title: String,
    val accountNumber: String,
    val type: String = "Personal / Send Money",
    val instructions: String = "Send Money to the number and enter TrxID below",
    val brandColorHex: Long = 0xFF00E5FF,
    val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GatewayItemDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "number") val number: String? = null,
    @Json(name = "account_number") val accountNumber: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "type") val type: String? = "Personal",
    @Json(name = "instructions") val instructions: String? = null,
    @Json(name = "note") val note: String? = null,
    @Json(name = "color") val color: String? = null,
    @Json(name = "icon") val icon: String? = null,
    @Json(name = "active") val rawActive: Any? = true,
    @Json(name = "is_active") val rawIsActive: Any? = null,
    @Json(name = "status") val status: String? = null
) {
    val isActive: Boolean
        get() {
            if (status != null && status.equals("inactive", ignoreCase = true)) return false
            val check = rawIsActive ?: rawActive ?: true
            return when (check) {
                is Boolean -> check
                is Number -> check.toInt() == 1
                is String -> check.equals("1") || check.equals("true", ignoreCase = true)
                else -> true
            }
        }

    val effectiveNumber: String get() = (number ?: accountNumber ?: phone ?: "").trim()
    val effectiveName: String get() = name ?: title ?: id?.replaceFirstChar { it.uppercase() } ?: "Payment Gateway"
    val effectiveInstructions: String get() = (instructions ?: note ?: "Send exact amount via Send Money to this $effectiveName number and enter TrxID below.").trim()

    fun toPaymentMethodModel(): PaymentMethodModel {
        val key = id?.lowercase() ?: effectiveName.lowercase()
        val parsedColor: Long = try {
            if (!color.isNullOrBlank()) {
                val cleanHex = color.replace("#", "")
                if (cleanHex.length == 6) (0xFF000000 or cleanHex.toLong(16)) else cleanHex.toLong(16)
            } else {
                when {
                    key.contains("bkash") -> 0xFFE2136E
                    key.contains("nagad") -> 0xFFF7941D
                    key.contains("rocket") -> 0xFF8C3494
                    else -> 0xFF00E5FF
                }
            }
        } catch (_: Exception) {
            0xFF00E5FF
        }

        return PaymentMethodModel(
            key = key,
            title = effectiveName,
            accountNumber = effectiveNumber,
            type = type ?: "Personal",
            instructions = effectiveInstructions,
            brandColorHex = parsedColor,
            isActive = isActive
        )
    }
}

@JsonClass(generateAdapter = true)
data class SubscriptionSubmitRequest(
    @Json(name = "user_id") val userId: Any,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "user_email") val userEmail: String? = null,
    @Json(name = "user_phone") val userPhone: String? = null,
    @Json(name = "plan_id") val planId: Any,
    @Json(name = "package_id") val packageId: Any? = null,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "payment_method") val paymentMethod: String,
    @Json(name = "gateway") val gateway: String? = null,
    @Json(name = "method") val method: String? = null,
    @Json(name = "trx_id") val trxId: String,
    @Json(name = "transaction_id") val transactionId: String? = null,
    @Json(name = "sender_number") val senderNumber: String? = null,
    @Json(name = "sender_phone") val senderPhone: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "amount") val amount: Double? = null,
    @Json(name = "price") val price: Double? = null,
    @Json(name = "notes") val notes: String? = null,
    @Json(name = "status") val status: String? = "pending"
)

@JsonClass(generateAdapter = true)
data class InvoiceItemDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "invoice_id") val invoiceId: String? = null,
    @Json(name = "submission_id") val submissionId: String? = null,
    @Json(name = "plan_id") val rawPlanId: Any? = null,
    @Json(name = "plan_name") val rawPlanName: String? = null,
    @Json(name = "amount") val rawAmount: Any? = null,
    @Json(name = "payment_method") val rawPaymentMethod: String? = null,
    @Json(name = "gateway") val gateway: String? = null,
    @Json(name = "sender_number") val senderNumber: String? = null,
    @Json(name = "trx_id") val rawTrxId: String? = null,
    @Json(name = "transaction_id") val transactionId: String? = null,
    @Json(name = "status") val rawStatus: String? = "pending",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "date") val date: String? = null
) {
    val id: String get() = rawId?.toString() ?: invoiceId ?: submissionId ?: "INV-${(1000..9999).random()}"
    val planName: String get() = rawPlanName?.takeIf { it.isNotBlank() } ?: "VIP Membership Pass"
    val paymentMethod: String get() = rawPaymentMethod ?: gateway ?: "bKash"
    val trxId: String get() = (rawTrxId ?: transactionId ?: "N/A").trim()
    val status: String get() = (rawStatus ?: "pending").lowercase()
    val displayDate: String get() = date ?: createdAt ?: "Recent"
    val displayAmount: String
        get() {
            val amt = rawAmount ?: 99
            return when (amt) {
                is Number -> "৳ ${amt.toInt()}"
                is String -> if (amt.startsWith("৳")) amt else "৳ $amt"
                else -> "৳ 99"
            }
        }
}

@JsonClass(generateAdapter = true)
data class SubscriptionSubmitResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String = "Payment submitted successfully. It will be verified shortly.",
    @Json(name = "submission_id") val submissionId: String? = null,
    @Json(name = "invoice_id") val invoiceId: String? = null,
    @Json(name = "status") val status: String = "pending",
    @Json(name = "invoice") val invoice: InvoiceItemDto? = null,
    @Json(name = "data") val data: InvoiceItemDto? = null
) {
    val effectiveInvoiceId: String
        get() = submissionId ?: invoiceId ?: invoice?.id ?: data?.id ?: "SUB-${(10000..99999).random()}"
}

@JsonClass(generateAdapter = true)
data class PendingSubscriptionRequestModel(
    val userId: String = "",
    val submissionId: String = "",
    val planId: String = "",
    val planName: String = "",
    val amount: Double = 0.0,
    val paymentMethod: String = "",
    val senderNumber: String = "",
    val transactionId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "pending"
)

@JsonClass(generateAdapter = true)
data class SubscriptionStatusResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: String? = "inactive",
    @Json(name = "is_vip") val rawIsVip: Boolean? = null,
    @Json(name = "vip") val rawVip: Boolean? = null,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "plan_expires_at") val planExpiresAt: String? = null,
    @Json(name = "expires_at") val rawExpiresAt: String? = null,
    @Json(name = "days_remaining") val rawDaysRemaining: Int? = null,
    @Json(name = "invoices") val invoices: List<InvoiceItemDto> = emptyList(),
    @Json(name = "history") val history: List<InvoiceItemDto> = emptyList()
) {
    val isVip: Boolean get() = rawIsVip == true || rawVip == true || status.equals("active", ignoreCase = true)
    val expiresAt: String? get() = planExpiresAt ?: rawExpiresAt
    val daysRemaining: Int get() = rawDaysRemaining ?: if (isVip) 30 else 0
    val allInvoices: List<InvoiceItemDto> get() = invoices.ifEmpty { history }
}
