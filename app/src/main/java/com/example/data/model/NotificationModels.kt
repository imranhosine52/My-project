package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
    @Json(name = "poster") val poster: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner") val banner: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "time_ago") val customTimeAgo: String? = null,
    @Json(name = "is_read") val isRead: Boolean = false
) {
    val id: String get() = rawId?.toString() ?: title.hashCode().toString()
    val timeAgo: String get() = customTimeAgo ?: createdAt ?: "Recent"

    val effectivePoster: String?
        get() {
            val raw = (posterUrl ?: poster ?: bannerUrl ?: banner)?.trim() ?: return null
            return when {
                raw.startsWith("http://") || raw.startsWith("https://") -> raw
                raw.startsWith("/") -> "https://playdramaflix.com$raw"
                else -> "https://playdramaflix.com/$raw"
            }
        }

    val targetSlug: String
        get() {
            val direct = slug ?: contentSlug ?: postSlug
            if (!direct.isNullOrBlank()) return direct.trim().trimStart('/')
            if (!url.isNullOrBlank()) {
                val clean = url.trim().trimStart('/')
                    .removePrefix("watch/")
                    .removePrefix("drama/")
                    .removePrefix("content/")
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
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: "🚀 New Update Available!"
    val displayMessage: String get() = message?.takeIf { it.isNotBlank() } ?: "Bug fixes and performance improvements. Update now to continue watching!"
    val targetDownloadUrl: String get() = downloadUrl?.takeIf { it.isNotBlank() } ?: "https://playdramaflix.com/downloads/app-latest.apk"
}
