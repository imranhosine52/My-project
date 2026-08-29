package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ContentResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Any? = null,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "data") val data: List<ContentItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ContentItemDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "type") val type: String = "series", // "movie" | "series" | "shorts" | "anime"
    @Json(name = "title") val title: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "description") val description: String? = null,
    @Json(name = "meta_description") val metaDescription: String? = null,
    @Json(name = "language") val language: String = "Bangla Dubbed",
    @Json(name = "dub_badge") val customDubBadge: String? = null,
    @Json(name = "release_year") val releaseYear: String = "2026",
    @Json(name = "rating") val rawRating: Any? = "8.5",
    @Json(name = "views") val rawViews: Any? = 0,
    @Json(name = "categories") val rawCategories: Any? = null,
    @Json(name = "total_episodes") val rawTotalEpisodes: Any? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "share_url") val shareUrl: String? = null,
    @Json(name = "synopsis") val customSynopsis: String? = null,
    @Json(name = "is_featured") val isFeatured: Boolean = false,
    @Json(name = "is_recent") val isRecent: Boolean = false,
    @Json(name = "is_hot") val isHot: Boolean = false
) {
    val id: String
        get() = rawId?.toString() ?: slug

    val rating: Double
        get() = when (rawRating) {
            is Number -> rawRating.toDouble()
            is String -> rawRating.toDoubleOrNull() ?: 8.5
            else -> 8.5
        }

    val views: String
        get() = when (rawViews) {
            is Number -> "${rawViews} views"
            is String -> rawViews
            else -> "0 views"
        }

    val numericViews: Long
        get() {
            return when (val v = rawViews) {
                is Number -> v.toLong()
                is String -> {
                    val clean = v.trim().uppercase().replace("VIEWS", "").replace("VIEW", "").trim()
                    when {
                        clean.endsWith("M") -> ((clean.dropLast(1).toDoubleOrNull() ?: 1.0) * 1_000_000).toLong()
                        clean.endsWith("K") -> ((clean.dropLast(1).toDoubleOrNull() ?: 1.0) * 1_000).toLong()
                        else -> clean.toLongOrNull() ?: 0L
                    }
                }
                else -> 0L
            }
        }

    val viewsDisplay: String
        get() {
            val n = numericViews
            return when {
                n >= 1_000_000 -> "${(n / 100_000) / 10.0}M"
                n >= 1_000 -> "${(n / 100) / 10.0}K"
                n > 0 -> "$n"
                rawViews is String && (rawViews as String).isNotBlank() -> rawViews as String
                else -> "100K"
            }
        }

    val categories: List<String>
        get() = when (rawCategories) {
            is List<*> -> rawCategories.filterIsInstance<String>().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
            is String -> if (rawCategories.isNotBlank()) rawCategories.split(",").map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
            else -> emptyList()
        }

    val totalEpisodes: Int
        get() {
            val num = when (rawTotalEpisodes) {
                is Number -> rawTotalEpisodes.toInt()
                is String -> rawTotalEpisodes.toIntOrNull() ?: 0
                else -> 0
            }
            return if (num > 0) num else 1
        }

    val isSpotlight: Boolean
        get() = isFeatured || isHot

    val isRecentlyAdded: Boolean
        get() = isFeatured || releaseYear == "2026" || releaseYear == "2025" || title.contains("Guess Who I Am", ignoreCase = true)

    val watchUrl: String
        get() = shareUrl ?: "https://playdramaflix.com/watch/$slug"

    val isShorts: Boolean
        get() = type.equals("shorts", ignoreCase = true) ||
                categories.any { it.contains("Shorts", ignoreCase = true) } ||
                title.contains("Shorts", ignoreCase = true) ||
                slug.contains("shorts", ignoreCase = true) ||
                (rawId?.toString()?.startsWith("s") == true && type != "series" && type != "anime" && type != "movie")

    val isAnime: Boolean
        get() = type.equals("anime", ignoreCase = true) ||
                categories.any { it.contains("Anime", ignoreCase = true) } ||
                title.contains("Anime", ignoreCase = true) ||
                slug.contains("anime", ignoreCase = true) ||
                title.contains("Jujutsu", ignoreCase = true) ||
                title.contains("Solo Leveling", ignoreCase = true) ||
                title.contains("Overflow", ignoreCase = true) ||
                title.contains("Demon Slayer", ignoreCase = true) ||
                title.contains("Death Note", ignoreCase = true) ||
                title.contains("Chainsaw Man", ignoreCase = true) ||
                title.contains("Attack on Titan", ignoreCase = true) ||
                title.contains("Naruto", ignoreCase = true) ||
                title.contains("One Piece", ignoreCase = true)

    val isMovie: Boolean
        get() = !isShorts && !isAnime && (
                type.equals("movie", ignoreCase = true) ||
                type.equals("film", ignoreCase = true) ||
                categories.any { it.contains("Movie", ignoreCase = true) || it.contains("Film", ignoreCase = true) } ||
                title.contains("Movie", ignoreCase = true) ||
                slug.contains("movie", ignoreCase = true)
        )

    val isDramaSeries: Boolean
        get() = !isShorts && !isAnime && !isMovie && (
                type.equals("series", ignoreCase = true) ||
                type.equals("drama", ignoreCase = true) ||
                categories.any { it.contains("Drama Series", ignoreCase = true) || it.contains("Drama", ignoreCase = true) || it.contains("Series", ignoreCase = true) } ||
                title.contains("Season", ignoreCase = true) ||
                totalEpisodes > 1
        )

    val isBanglaDub: Boolean
        get() = (language.contains("Bangla", ignoreCase = true) ||
                dubBadge.contains("Bangla", ignoreCase = true) ||
                title.contains("Bangla", ignoreCase = true) ||
                title.contains("Bengali", ignoreCase = true) ||
                categories.any { it.contains("Bangla", ignoreCase = true) }) &&
                !language.startsWith("Hindi", ignoreCase = true)

    val isHindiDub: Boolean
        get() = (language.contains("Hindi", ignoreCase = true) ||
                dubBadge.contains("Hindi", ignoreCase = true) ||
                title.contains("Hindi", ignoreCase = true) ||
                categories.any { it.contains("Hindi", ignoreCase = true) }) &&
                !language.startsWith("Bangla", ignoreCase = true)

    val dubBadge: String
        get() {
            if (!customDubBadge.isNullOrBlank()) return customDubBadge
            val lower = language.lowercase()
            return when {
                lower.contains("bangla") -> "Bangla Dub"
                lower.contains("hindi") -> "Hindi Dub"
                lower.contains("dual") -> "Dual Audio"
                else -> "Bangla Dub"
            }
        }

    val synopsis: String
        get() = description?.takeIf { it.isNotBlank() } ?: customSynopsis ?: metaDescription ?: "Watch full episodes in HD on PlayDramaFlix."

    val trailerUrl: String
        get() = shareUrl ?: ""

    val quality: String
        get() = "1080p Full HD"

    val viewsCount: Long
        get() = numericViews

    val country: String
        get() = when {
            title.contains("Korea", ignoreCase = true) || categories.any { it.contains("k-drama", ignoreCase = true) || it.contains("korean", ignoreCase = true) } -> "South Korea"
            title.contains("China", ignoreCase = true) || categories.any { it.contains("c-drama", ignoreCase = true) || it.contains("chinese", ignoreCase = true) } -> "China"
            isAnime || categories.any { it.contains("Japan", ignoreCase = true) || it.contains("anime", ignoreCase = true) } -> "Japan"
            else -> "Asia"
        }
}

@JsonClass(generateAdapter = true)
data class WatchDetailResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Any? = null,
    @Json(name = "content") val content: ContentItemDto? = null,
    @Json(name = "servers") val servers: List<ServerDto> = emptyList(),
    @Json(name = "episodes") val episodes: List<EpisodeDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ServerDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "content_id") val rawContentId: Any? = null,
    @Json(name = "episode_id") val rawEpisodeId: Any? = null,
    @Json(name = "server_name") val serverName: String? = "Server 1 (Byse.sx)",
    @Json(name = "raw_url") val rawUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "server_type") val serverType: String? = "stream",
    @Json(name = "quality") val quality: String? = "Streaming"
) {
    val id: String get() = rawId?.toString() ?: ""
    val name: String get() = serverName ?: "Server 1 (Byse.sx)"
    val episodeId: String get() = rawEpisodeId?.toString() ?: ""
    val url: String get() = embedUrl?.takeIf { it.isNotBlank() } ?: rawUrl ?: ""
    val type: String get() = if (serverType == "hls" || url.endsWith(".m3u8")) "hls" else if (url.contains("/e/") || url.contains("embed") || url.contains("byse")) "embed" else "embed"
}

@JsonClass(generateAdapter = true)
data class EpisodeDto(
    @Json(name = "episode_id") val rawEpisodeId: Any? = null,
    @Json(name = "episode_number") val episodeNumber: Int = 1,
    @Json(name = "ep_title") val epTitle: String = "Episode 1",
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "duration") val duration: String = "24m",
    @Json(name = "video_url") val videoUrl: String? = null,
    @Json(name = "embed_url") val embedUrl: String? = null,
    @Json(name = "is_locked") val isLocked: Boolean = false,
    @Json(name = "ads_count") val adsCount: Int = 0
) {
    val episodeId: String get() = rawEpisodeId?.toString() ?: episodeNumber.toString()
}

@JsonClass(generateAdapter = true)
data class NotificationResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Any? = null,
    @Json(name = "total") val total: Int? = 0,
    @Json(name = "data") val data: List<NotificationItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NotificationItemDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "title") val title: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "url") val url: String? = null,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "content_slug") val contentSlug: String? = null,
    @Json(name = "post_slug") val postSlug: String? = null,
    @Json(name = "content_id") val contentId: Any? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "icon_type") val iconType: String = "series",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "time_ago") val customTimeAgo: String? = null,
    @Json(name = "is_read") val isRead: Boolean = false
) {
    val id: String get() = rawId?.toString() ?: title.hashCode().toString()
    val timeAgo: String get() = customTimeAgo ?: createdAt ?: "Recent"
    val effectivePoster: String? get() = posterUrl ?: image ?: thumbnail
    val targetSlug: String get() {
        val direct = slug ?: contentSlug ?: postSlug
        if (!direct.isNullOrBlank()) return direct.trim().trimStart('/')
        if (!url.isNullOrBlank()) {
            val clean = url.trim().trimStart('/')
                .removePrefix("watch/")
                .removePrefix("drama/")
                .removePrefix("content/")
                .removePrefix("series/")
                .removePrefix("post/")
            if (clean.isNotBlank()) return clean
        }
        return ""
    }
}

@JsonClass(generateAdapter = true)
data class DeviceRegisterRequest(
    @Json(name = "device_token") val deviceToken: String,
    @Json(name = "onesignal_player_id") val onesignalPlayerId: String? = null,
    @Json(name = "platform") val platform: String = "android",
    @Json(name = "app_version") val appVersion: String = "1.0.0",
    @Json(name = "device_model") val deviceModel: String = "Android Device",
    @Json(name = "os_version") val osVersion: String = "14"
)

// ======================= SUBSCRIPTION & VIP PAYMENT MODELS =======================

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
    val activePlans: List<SubscriptionPlanDto>
        get() = plans.filter { it.isActive }

    fun getPaymentMethodList(): List<PaymentMethodModel> {
        return paymentGateways.map { it.toPaymentMethodModel() }
    }
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
            if (status != null && (status.equals("inactive", ignoreCase = true) || status.equals("disabled", ignoreCase = true) || status.equals("0"))) return false
            val checkActive = rawIsActive ?: rawActive ?: true
            return when (checkActive) {
                is Boolean -> checkActive
                is Number -> checkActive.toInt() == 1
                is String -> checkActive.equals("1") || checkActive.equals("true", ignoreCase = true) || checkActive.equals("active", ignoreCase = true)
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
        get() = if (priceDouble % 1.0 == 0.0) priceDouble.toInt().toString() else String.format(java.util.Locale.US, "%.2f", priceDouble)

    val originalPriceDouble: Double
        get() = when (rawOriginalPrice) {
            is Number -> rawOriginalPrice.toDouble()
            is String -> rawOriginalPrice.toDoubleOrNull() ?: (priceDouble * 1.5)
            else -> (priceDouble * 1.5)
        }

    val originalPriceFormatted: String
        get() = if (originalPriceDouble % 1.0 == 0.0) originalPriceDouble.toInt().toString() else String.format(java.util.Locale.US, "%.2f", originalPriceDouble)

    val discountPercent: Int
        get() {
            val diff = originalPriceDouble - priceDouble
            return if (originalPriceDouble > 0 && diff > 0) ((diff / originalPriceDouble) * 100).toInt() else 0
        }

    val features: List<String>
        get() = when (rawFeatures) {
            is List<*> -> rawFeatures.filterIsInstance<String>().ifEmpty {
                listOf(
                    "Ad-Free Ultra Fast Full HD (1080p) Streaming",
                    "Unlock All Ongoing & Upcoming Dubbed Episodes",
                    "Unlimited Video Downloads for Offline Watching",
                    "Dedicated VIP High Speed Streaming Servers"
                )
            }
            is String -> rawFeatures.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty {
                listOf(
                    "Ad-Free Ultra Fast Full HD (1080p) Streaming",
                    "Unlock All Ongoing & Upcoming Dubbed Episodes",
                    "Unlimited Video Downloads for Offline Watching",
                    "Dedicated VIP High Speed Streaming Servers"
                )
            }
            else -> listOf(
                "Ad-Free Ultra Fast Full HD (1080p) Streaming",
                "Unlock All Ongoing & Upcoming Dubbed Episodes",
                "Unlimited Video Downloads for Offline Watching",
                "Dedicated VIP High Speed Streaming Servers"
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
            if (status != null && (status.equals("inactive", ignoreCase = true) || status.equals("disabled", ignoreCase = true) || status.equals("0"))) return false
            val checkActive = rawIsActive ?: rawActive ?: true
            return when (checkActive) {
                is Boolean -> checkActive
                is Number -> checkActive.toInt() == 1
                is String -> checkActive.equals("1") || checkActive.equals("true", ignoreCase = true) || checkActive.equals("active", ignoreCase = true)
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
                if (cleanHex.length == 6) {
                    (0xFF000000 or cleanHex.toLong(16))
                } else {
                    cleanHex.toLong(16)
                }
            } else {
                when {
                    key.contains("bkash") -> 0xFFE2136E
                    key.contains("nagad") -> 0xFFF7941D
                    key.contains("rocket") -> 0xFF8C3494
                    key.contains("upay") -> 0xFF00A2E8
                    else -> 0xFF00E5FF
                }
            }
        } catch (_: Exception) {
            when {
                key.contains("bkash") -> 0xFFE2136E
                key.contains("nagad") -> 0xFFF7941D
                key.contains("rocket") -> 0xFF8C3494
                key.contains("upay") -> 0xFF00A2E8
                else -> 0xFF00E5FF
            }
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
    @Json(name = "comment") val comment: String? = null,
    @Json(name = "promo_code") val promoCode: String? = null,
    @Json(name = "device_id") val deviceId: String? = null,
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
    @Json(name = "price") val rawPrice: Any? = null,
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
    val amount: Double get() = (rawAmount as? Number)?.toDouble() ?: (rawAmount as? String)?.toDoubleOrNull() ?: 99.0
    val displayAmount: String get() {
        val amt = rawAmount ?: rawPrice ?: 99
        return when (amt) {
            is Number -> "৳ ${amt.toInt()}"
            is String -> if (amt.startsWith("৳") || amt.startsWith("$")) amt else "৳ $amt"
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
    @Json(name = "user_id") val userId: String = "",
    @Json(name = "submission_id") val submissionId: String = "",
    @Json(name = "plan_id") val planId: String = "",
    @Json(name = "plan_name") val planName: String = "",
    @Json(name = "amount") val amount: Double = 0.0,
    @Json(name = "payment_method") val paymentMethod: String = "",
    @Json(name = "sender_number") val senderNumber: String = "",
    @Json(name = "transaction_id") val transactionId: String = "",
    @Json(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @Json(name = "status") val status: String = "pending"
)

@JsonClass(generateAdapter = true)
data class SubscriptionStatusResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: String? = "inactive",
    @Json(name = "is_vip") val rawIsVip: Boolean? = null,
    @Json(name = "vip") val rawVip: Boolean? = null,
    @Json(name = "is_active_vip") val rawIsActiveVip: Boolean? = null,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "plan_expires_at") val planExpiresAt: String? = null,
    @Json(name = "expires_at") val rawExpiresAt: String? = null,
    @Json(name = "days_remaining") val rawDaysRemaining: Int? = null,
    @Json(name = "days_left") val rawDaysLeft: Int? = null,
    @Json(name = "invoices") val invoices: List<InvoiceItemDto> = emptyList(),
    @Json(name = "history") val history: List<InvoiceItemDto> = emptyList(),
    @Json(name = "invoice_history") val invoiceHistory: List<InvoiceItemDto> = emptyList(),
    @Json(name = "message") val message: String? = null
) {
    val isVip: Boolean
        get() = rawIsVip == true || rawVip == true || rawIsActiveVip == true || status.equals("active", ignoreCase = true)

    val expiresAt: String?
        get() = planExpiresAt ?: rawExpiresAt

    val daysRemaining: Int
        get() = rawDaysRemaining ?: rawDaysLeft ?: if (isVip) 30 else 0

    val daysLeft: Int?
        get() = daysRemaining

    val allInvoices: List<InvoiceItemDto>
        get() = invoices.ifEmpty { history }.ifEmpty { invoiceHistory }
}

// ======================= POST ENGAGEMENT & INTERACTION MODELS =======================

@JsonClass(generateAdapter = true)
data class ViewIncrementRequest(
    @Json(name = "content_id") val contentId: Any
)

@JsonClass(generateAdapter = true)
data class ViewIncrementResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "content_id") val contentId: Any? = null,
    @Json(name = "total_views") val totalViews: Long? = null,
    @Json(name = "views") val views: Long? = null
) {
    val effectiveViews: Long
        get() = totalViews ?: views ?: 0L
}

@JsonClass(generateAdapter = true)
data class LikeToggleRequest(
    @Json(name = "content_id") val contentId: Any,
    @Json(name = "episode_id") val episodeId: Any? = null
)

@JsonClass(generateAdapter = true)
data class LikeToggleResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "liked") val liked: Boolean? = null,
    @Json(name = "user_liked") val userLiked: Boolean? = null,
    @Json(name = "total_likes") val totalLikes: Long? = null,
    @Json(name = "likes") val likes: Long? = null,
    @Json(name = "likes_count") val likesCount: Long? = null,
    @Json(name = "like_count") val likeCount: Long? = null
) {
    val effectiveIsLiked: Boolean
        get() = isLiked ?: liked ?: userLiked ?: false

    val effectiveLikes: Long
        get() = totalLikes ?: likes ?: likesCount ?: likeCount ?: 0L
}

@JsonClass(generateAdapter = true)
data class InteractionStatusResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "content_id") val contentId: Any? = null,
    @Json(name = "views") val views: Long? = null,
    @Json(name = "total_views") val totalViews: Long? = null,
    @Json(name = "views_count") val viewsCount: Long? = null,
    @Json(name = "view_count") val viewCount: Long? = null,
    @Json(name = "total_likes") val totalLikes: Long? = null,
    @Json(name = "likes") val likes: Long? = null,
    @Json(name = "likes_count") val likesCount: Long? = null,
    @Json(name = "like_count") val likeCount: Long? = null,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "liked") val liked: Boolean? = null,
    @Json(name = "user_liked") val userLiked: Boolean? = null,
    @Json(name = "total_comments") val totalComments: Int? = null,
    @Json(name = "comments_count") val commentsCount: Int? = null,
    @Json(name = "comment_count") val commentCount: Int? = null
) {
    val effectiveViews: Long
        get() = totalViews ?: views ?: viewsCount ?: viewCount ?: 0L

    val effectiveLikes: Long
        get() = totalLikes ?: likes ?: likesCount ?: likeCount ?: 0L

    val effectiveIsLiked: Boolean
        get() = isLiked ?: liked ?: userLiked ?: false

    val effectiveCommentsCount: Int
        get() = totalComments ?: commentsCount ?: commentCount ?: 0
}

@JsonClass(generateAdapter = true)
data class DramaApiComment(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "content_id") val rawContentId: Any? = null,
    @Json(name = "episode_id") val rawEpisodeId: Any? = null,
    @Json(name = "parent_id") val rawParentId: Any? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "user_avatar") val userAvatar: String? = null,
    @Json(name = "avatar") val fallbackAvatar: String? = null,
    @Json(name = "handle") val handle: String? = null,
    @Json(name = "comment_text") val commentText: String = "",
    @Json(name = "likes_count") val rawLikesCount: Int? = null,
    @Json(name = "likes") val fallbackLikes: Int? = null,
    @Json(name = "shares_count") val rawSharesCount: Int? = null,
    @Json(name = "shares") val fallbackShares: Int? = null,
    @Json(name = "is_liked") val isLikedVal: Boolean? = null,
    @Json(name = "replies_count") val rawRepliesCount: Int? = null,
    @Json(name = "date_display") val dateDisplay: String? = null,
    @Json(name = "time_ago") val timeAgo: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "replies") val replies: List<DramaApiComment>? = null
) {
    val id: String get() = rawId?.toString() ?: "c_${System.currentTimeMillis()}"
    val contentId: String get() = rawContentId?.toString() ?: ""
    val episodeId: String? get() = rawEpisodeId?.toString()
    val parentId: String? get() = rawParentId?.toString()
    val userId: String? get() = rawUserId?.toString()
    val displayName: String get() = userName?.takeIf { it.isNotBlank() } ?: "DramaFlix Fan"
    val displayHandle: String get() = handle?.takeIf { it.isNotBlank() } ?: "@${displayName.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }}"
    val avatarUrl: String? get() = userAvatar?.takeIf { it.isNotBlank() } ?: fallbackAvatar?.takeIf { it.isNotBlank() }
    val likesCount: Int get() = rawLikesCount ?: fallbackLikes ?: 0
    val sharesCount: Int get() = rawSharesCount ?: fallbackShares ?: 0
    val isLiked: Boolean get() = isLikedVal ?: false
    val isVipUser: Boolean get() = false
    val repliesCount: Int get() = rawRepliesCount ?: replies?.size ?: 0
    val displayDate: String get() = dateDisplay?.takeIf { it.isNotBlank() } ?: timeAgo?.takeIf { it.isNotBlank() } ?: createdAt?.take(10) ?: "Just now"
    val repliesList: List<DramaApiComment> get() = replies ?: emptyList()
}

typealias CommentItemDto = DramaApiComment

@JsonClass(generateAdapter = true)
data class CommentsListResponse(
    @Json(name = "status") val rawStatus: Any? = 200,
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "total_comments") val totalComments: Int? = null,
    @Json(name = "total") val fallbackTotal: Int? = null,
    @Json(name = "data") val data: List<DramaApiComment>? = null,
    @Json(name = "comments") val comments: List<DramaApiComment>? = null
) {
    val commentsList: List<DramaApiComment>
        get() = data ?: comments ?: emptyList()

    val effectiveTotal: Int
        get() = totalComments ?: fallbackTotal ?: commentsList.size
}

@JsonClass(generateAdapter = true)
data class AddCommentApiRequest(
    @Json(name = "content_id") val contentId: Any,
    @Json(name = "episode_id") val episodeId: Any? = null,
    @Json(name = "parent_id") val parentId: Any? = null,
    @Json(name = "user_id") val userId: Any? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "comment_text") val commentText: String
)

@JsonClass(generateAdapter = true)
data class AddCommentResponse(
    @Json(name = "status") val rawStatus: Any? = 201,
    @Json(name = "success") val success: Boolean? = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "data") val data: DramaApiComment? = null,
    @Json(name = "comment") val comment: DramaApiComment? = null
) {
    val commentItem: DramaApiComment?
        get() = data ?: comment
}

@JsonClass(generateAdapter = true)
data class CommentLikeApiRequest(
    @Json(name = "comment_id") val commentId: Any,
    @Json(name = "user_id") val userId: Any? = null
)

@JsonClass(generateAdapter = true)
data class CommentLikeApiResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val rawStatus: Any? = 200,
    @Json(name = "comment_id") val rawCommentId: Any? = null,
    @Json(name = "is_liked") val isLiked: Boolean? = null,
    @Json(name = "total_likes") val totalLikes: Int? = null
)

@JsonClass(generateAdapter = true)
data class CommentShareApiRequest(
    @Json(name = "comment_id") val commentId: Any,
    @Json(name = "user_id") val userId: Any? = null
)

@JsonClass(generateAdapter = true)
data class CommentShareApiResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val rawStatus: Any? = 200,
    @Json(name = "comment_id") val rawCommentId: Any? = null,
    @Json(name = "total_shares") val totalShares: Int? = null
)

@JsonClass(generateAdapter = true)
data class AppVersionCheckResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "update_available") val updateAvailable: Boolean = false,
    @Json(name = "force_update") val forceUpdate: Boolean = false,
    @Json(name = "latest_version") val latestVersion: String = "1.0.0",
    @Json(name = "min_required_version") val minRequiredVersion: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "download_url") val downloadUrl: String? = null,
    @Json(name = "changelog") val changelog: List<String>? = null
) {
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: "🚀 New Update Available!"

    val displayMessage: String
        get() = message?.takeIf { it.isNotBlank() }
            ?: "We have added faster video streaming nodes and fixed critical bugs. Please update now to continue watching!"

    val targetDownloadUrl: String
        get() = downloadUrl?.takeIf { it.isNotBlank() } ?: "https://playdramaflix.com/downloads/app-latest.apk"
}

// ======================= UNIFIED USER AUTH & PROFILE MODELS =======================

@JsonClass(generateAdapter = true)
data class GoogleAuthRequest(
    @Json(name = "google_id") val googleId: String,
    @Json(name = "email") val email: String,
    @Json(name = "name") val name: String,
    @Json(name = "avatar") val avatar: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAuthResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "message") val message: String? = "Google Authentication successful!",
    @Json(name = "user") val user: UserProfileDto? = null,
    @Json(name = "token") val token: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthRegisterRequest(
    @Json(name = "name") val name: String,
    @Json(name = "email_or_phone") val emailOrPhone: String,
    @Json(name = "password") val password: String,
    @Json(name = "avatar") val avatar: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthLoginRequest(
    @Json(name = "email_or_phone") val emailOrPhone: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "message") val message: String? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "token") val token: String? = null,
    @Json(name = "user") val user: UserProfileDto? = null,
    @Json(name = "is_vip") val isVip: Boolean? = false
) {
    val userId: String get() = rawUserId?.toString() ?: user?.id ?: ""
}

@JsonClass(generateAdapter = true)
data class UserProfileDto(
    @Json(name = "id") val rawId: Any? = null,
    @Json(name = "user_id") val rawUserId: Any? = null,
    @Json(name = "account_id") val accountId: String? = null,
    @Json(name = "uid") val rawUid: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "user_name") val userName: String? = null,
    @Json(name = "email") val email: String? = null,
    @Json(name = "phone") val phone: String? = null,
    @Json(name = "role") val role: String? = "user",
    @Json(name = "plan") val plan: String? = "free",
    @Json(name = "avatar") val avatar: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "profile_image") val profileImage: String? = null,
    @Json(name = "is_vip") val isVip: Boolean = false,
    @Json(name = "plan_name") val planName: String? = null,
    @Json(name = "vip_expiry") val vipExpiry: String? = null,
    @Json(name = "plan_expires_at") val planExpiresAt: String? = null,
    @Json(name = "vip_days_left") val vipDaysLeft: Int? = 0,
    @Json(name = "days_remaining") val daysRemaining: Int? = null,
    @Json(name = "has_biometric") val hasBiometric: Boolean? = false,
    @Json(name = "created_at") val createdAt: String? = null
) {
    val id: String get() = rawId?.toString() ?: rawUserId?.toString() ?: ""
    val effectiveAccountId: String get() = accountId ?: rawUid ?: run {
        val numId = (rawId as? Number)?.toLong() ?: rawId?.toString()?.toLongOrNull() ?: 10000000L
        "${77000000L + (numId % 999999L)}"
    }
    val displayName: String get() = name?.takeIf { it.isNotBlank() } ?: userName?.takeIf { it.isNotBlank() } ?: email?.substringBefore("@") ?: phone ?: "PlayDramaFlix User"
    val displayContact: String get() = email ?: phone ?: "Signed in with Google"
    val effectiveAvatar: String? get() = avatarUrl ?: profileImage ?: avatar
    val effectiveDaysLeft: Int get() = daysRemaining ?: vipDaysLeft ?: 0
    val effectiveExpiry: String? get() = planExpiresAt ?: vipExpiry
    val effectivePlan: String get() = plan ?: (if (isVip) "vip" else "free")
}

@JsonClass(generateAdapter = true)
data class UserProfileResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "message") val message: String? = null,
    @Json(name = "user") val user: UserProfileDto? = null
)

@JsonClass(generateAdapter = true)
data class AdsConfigResponse(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "status") val status: Int? = 200,
    @Json(name = "ads_enabled") val adsEnabled: Boolean = true,
    @Json(name = "primary_network") val primaryNetwork: String = "adsterra",
    @Json(name = "fallback_network") val fallbackNetwork: String = "startio",
    @Json(name = "startio") val startio: StartIoConfig? = StartIoConfig(),
    @Json(name = "admob") val admob: AdMobConfig? = AdMobConfig(),
    @Json(name = "adsterra") val adsterra: AdsterraConfig? = AdsterraConfig(),
    @Json(name = "rules") val rules: AdRulesConfig? = AdRulesConfig()
)

@JsonClass(generateAdapter = true)
data class AdsterraConfig(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "direct_link") val directLink: String? = null,
    @Json(name = "smartlink_url") val smartlinkUrl: String? = null,
    @Json(name = "popunder_url") val popunderUrl: String? = null,
    @Json(name = "popunder_frequency") val popunderFrequency: Int = 3,
    @Json(name = "popunder_min_interval_seconds") val popunderMinIntervalSeconds: Int = 30
) {
    val effectiveDirectLink: String?
        get() = directLink?.takeIf { it.isNotBlank() } ?: smartlinkUrl?.takeIf { it.isNotBlank() }
}

@JsonClass(generateAdapter = true)
data class StartIoConfig(
    @Json(name = "enabled") val enabled: Boolean = true,
    @Json(name = "app_id") val appId: String = "207238360",
    @Json(name = "publisher_id") val publisherId: String? = "113502454"
)

@JsonClass(generateAdapter = true)
data class AdMobConfig(
    @Json(name = "enabled") val enabled: Boolean = false,
    @Json(name = "app_id") val appId: String? = null,
    @Json(name = "banner_id") val bannerId: String? = null,
    @Json(name = "interstitial_id") val interstitialId: String? = null,
    @Json(name = "rewarded_id") val rewardedId: String? = null
)

@JsonClass(generateAdapter = true)
data class AdRulesConfig(
    @Json(name = "timer_seconds") val timerSeconds: Int = 10,
    @Json(name = "rewarded_unlock_hours") val rewardedUnlockHours: Int = 2,
    @Json(name = "free_unlocked_episodes") val freeUnlockedEpisodes: Int = 1
)
