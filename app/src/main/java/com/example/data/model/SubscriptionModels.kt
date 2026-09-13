package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.util.Locale

@JsonClass(generateAdapter = true)
data class CryptoNetworkDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "name") val name: String = "BSC (BEP20)",
    @Json(name = "address") val address: String = "0x9cc85d119b113914034913858ea30d1f9eb52d2e",
    @Json(name = "symbol") val symbol: String = "USDT / BNB",
    @Json(name = "icon") val icon: String? = null,
    @Json(name = "qr_code") val qrCode: String? = null
) {
    val id: String get() = rawId?.toString() ?: name
}

@JsonClass(generateAdapter = true)
data class SubscriptionPlansResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "subscription_enabled") val subscriptionEnabled: Boolean = true,
    @Json(name = "free_episodes_count") val freeEpisodesCount: Int? = 1,
    @Json(name = "total_plans") val totalPlans: Int? = 0,
    @Json(name = "plans") val plans: List<SubscriptionPlanDto> = emptyList(),
    @Json(name = "payment_gateways") val paymentGateways: List<GatewayItemDto> = emptyList(),
    @Json(name = "crypto_enabled") val cryptoEnabled: Boolean = true,
    @Json(name = "crypto_networks") val cryptoNetworks: List<CryptoNetworkDto> = emptyList()
) {
    val activePlans: List<SubscriptionPlanDto> get() = plans.filter { it.isActive }
}

@JsonClass(generateAdapter = true)
data class SubscriptionPlanDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "name") val name: String = "VIP Plan",
    @Json(name = "bangla_name") val banglaName: String? = null,
    @Json(name = "price") val rawPrice: Any? = "59.00",
    @Json(name = "original_price") val rawOriginalPrice: Any? = "88.50",
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
            is String -> rawPrice.toDoubleOrNull() ?: 59.0
            else -> 59.0
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
            return if (originalPriceDouble > 0 && diff > 0) ((diff / originalPriceDouble) * 100).toInt() else 33
        }

    val features: List<String>
        get() = when (rawFeatures) {
            is List<*> -> rawFeatures.filterIsInstance<String>()
            is String -> rawFeatures.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }
            else -> listOf(
                "Zero Ads Ultra Fast Full HD (1080p) Streaming",
                "Unlock All Ongoing & Upcoming Dubbed Episodes",
                "Unlimited High-Speed Video Downloads",
                "Support Stream on Multiple Devices Simultaneously"
            )
        }
}

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
    @Json(name = "color") val color: String? = null,
    @Json(name = "icon") val icon: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "active") val rawActive: Any? = true,
    @Json(name = "is_active") val rawIsActive: Any? = null
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

    val effectiveNumber: String get() = (number ?: accountNumber ?: phone ?: "01330049110").trim()
    val effectiveName: String get() = name ?: title ?: id?.replaceFirstChar { it.uppercase() } ?: "Payment Gateway"
}

@JsonClass(generateAdapter = true)
data class SubscriptionSubmitRequest(
    @Json(name = "user_id") val userId: Any,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "user_email") val userEmail: String? = null,
    @Json(name = "user_phone") val userPhone: String? = null,
    @Json(name = "plan_id") val planId: Any,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "payment_method") val paymentMethod: String,
    @Json(name = "crypto_network") val cryptoNetwork: String? = null,
    @Json(name = "trx_id") val trxId: String,
    @Json(name = "sender_number") val senderNumber: String? = null,
    @Json(name = "amount") val amount: Double? = null,
    @Json(name = "notes") val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class InvoiceItemDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "invoice_id") val invoiceId: String? = null,
    @Json(name = "invoice_no") val invoiceNo: String? = null,
    @Json(name = "submission_id") val submissionId: String? = null,
    @Json(name = "plan_id") val rawPlanId: Any? = null,
    @Json(name = "plan_name") val rawPlanName: String? = null,
    @Json(name = "amount") val rawAmount: Any? = null,
    @Json(name = "payment_method") val rawPaymentMethod: String? = null,
    @Json(name = "sender_number") val senderNumber: String? = null,
    @Json(name = "trx_id") val rawTrxId: String? = null,
    @Json(name = "status") val rawStatus: String? = "pending",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "date") val date: String? = null
) {
    val id: String get() = rawId?.toString() ?: invoiceNo ?: invoiceId ?: submissionId ?: "INV-${(1000..9999).random()}"
    val planName: String get() = rawPlanName?.takeIf { it.isNotBlank() } ?: "VIP Membership Pass"
    val paymentMethod: String get() = rawPaymentMethod ?: "bKash"
    val trxId: String get() = (rawTrxId ?: "N/A").trim()
    val status: String get() = (rawStatus ?: "pending").lowercase()
    val displayDate: String get() = date ?: createdAt ?: "Recent"
    val displayAmount: String
        get() {
            val amt = rawAmount ?: 59
            return when (amt) {
                is Number -> "৳ ${amt.toInt()}"
                is String -> if (amt.startsWith("৳") || amt.startsWith("$")) amt else "৳ $amt"
                else -> "৳ 59"
            }
        }
}

@JsonClass(generateAdapter = true)
data class SubscriptionSubmitResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "auto_approved") val autoApproved: Boolean? = false,
    @Json(name = "is_vip") val isVip: Boolean? = false,
    @Json(name = "message") val message: String = "Payment request submitted successfully.",
    @Json(name = "submission_id") val submissionId: String? = null,
    @Json(name = "invoice_id") val invoiceId: String? = null,
    @Json(name = "status") val status: String? = "pending",
    @Json(name = "invoice") val invoice: InvoiceItemDto? = null
) {
    val isAutoApproved: Boolean get() = autoApproved == true || isVip == true || status.equals("approved", ignoreCase = true)
    val effectiveInvoiceId: String get() = submissionId ?: invoiceId ?: invoice?.id ?: "SUB-${(10000..99999).random()}"
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
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "plan_expires_at") val planExpiresAt: String? = null,
    @Json(name = "expires_at") val rawExpiresAt: String? = null,
    @Json(name = "days_remaining") val rawDaysRemaining: Int? = null,
    @Json(name = "invoices") val invoices: List<InvoiceItemDto> = emptyList(),
    @Json(name = "history") val history: List<InvoiceItemDto> = emptyList()
) {
    val isVip: Boolean get() = rawIsVip == true || status.equals("active", ignoreCase = true) || status.equals("approved", ignoreCase = true)
    val expiresAt: String? get() = planExpiresAt ?: rawExpiresAt
    val daysRemaining: Int get() = rawDaysRemaining ?: if (isVip) 30 else 0
    val allInvoices: List<InvoiceItemDto> get() = invoices.ifEmpty { history }
}
