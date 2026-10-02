package com.example.ui.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.example.data.model.LocalVideoItem
import org.json.JSONObject

/**
 * 🎯 ইন্টেন্ট প্রসেসিং ফলাফল ডেটা মডেল
 */
data class ProcessedIntentResult(
    val targetSlug: String? = null,
    val isShorts: Boolean = false,
    val externalMediaItem: LocalVideoItem? = null,
    val browserUrl: String? = null,
    val openCommunityChat: Boolean = false,
    val openVipScreen: Boolean = false,
    val targetReelId: Int? = null,
    val targetPageId: Int? = null,
    val openUpdateDialog: Boolean = false
)

/**
 * ⚡ ডিপ-লিংক, পুশ নোটিফিকেশন ও এক্সটার্নাল মিডিয়া ফাইল পার্সিং ইঞ্জিন
 */
object IntentDeepLinkHandler {

    /**
     * কাঁচা ইনপুট বা URL থেকে ক্লিন ড্রামা স্লাগ এক্সট্র্যাক্ট করে
     */
    fun extractCleanSlug(input: String?): String? {
        if (input.isNullOrBlank()) return null
        var str = input.trim()

        // ১. যদি JSON স্ট্রিং আকারে থাকে
        if (str.startsWith("{") && str.endsWith("}")) {
            try {
                val json = JSONObject(str)
                str = json.optString("slug").takeIf { it.isNotBlank() }
                    ?: json.optString("content_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("post_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("target_slug").takeIf { it.isNotBlank() }
                    ?: json.optString("url").takeIf { it.isNotBlank() }
                    ?: json.optString("link").takeIf { it.isNotBlank() }
                    ?: str
            } catch (_: Exception) {}
        }

        // ২. যদি URL স্কিম থাকে
        if (str.startsWith("http://", ignoreCase = true) ||
            str.startsWith("https://", ignoreCase = true) ||
            str.startsWith("playdramaflix://", ignoreCase = true) ||
            str.startsWith("dramaflix://", ignoreCase = true)
        ) {
            val uri = runCatching { Uri.parse(str) }.getOrNull()
            val querySlug = uri?.getQueryParameter("slug") ?: uri?.getQueryParameter("id")
            if (!querySlug.isNullOrBlank()) {
                return querySlug.trim()
            }
            str = uri?.path ?: ""
        }

        // ৩. পাথ প্রিফিক্স ক্লিন করা
        str = str.trim('/')
            .removePrefix("watch/")
            .removePrefix("drama/")
            .removePrefix("series/")
            .removePrefix("content/")
            .removePrefix("shorts/")
            .removePrefix("video/")
            .removePrefix("movie/")
            .removePrefix("post/")
            .trim('/')

        if (str.contains(" ")) {
            str = str.replace(Regex("\\s+"), "-")
        }

        return str.takeIf {
            it.isNotBlank() &&
            !it.contains("://") &&
            !it.startsWith("topics/") &&
            !it.equals("high", ignoreCase = true) &&
            !it.equals("default", ignoreCase = true) &&
            !it.equals("normal", ignoreCase = true) &&
            !it.equals("home", ignoreCase = true) &&
            !it.equals("index.php", ignoreCase = true) &&
            !it.equals("index.html", ignoreCase = true)
        }
    }

    /**
     * অ্যাক্টিভিটিতে আসা যেকোনো ইনকামিং ইন্টেন্ট সম্পূর্ণ পার্স করে ফলাফল রিটার্ন করে
     */
    fun processIncomingIntent(context: Context, intent: Intent?): ProcessedIntentResult {
        if (intent == null) return ProcessedIntentResult()

        val extras = intent.extras
        val dataUri: Uri? = intent.data
        val dataUriString = dataUri?.toString() ?: ""
        val action = intent.action ?: ""

        // ক) রিলস ডিপ-লিংক
        if (dataUriString.contains("/reel/") || dataUriString.startsWith("playdramaflix://reel")) {
            val rId = dataUri?.lastPathSegment?.toIntOrNull()
            if (rId != null) return ProcessedIntentResult(targetReelId = rId)
        }

        // খ) পেজ ডিপ-লিংক
        if (dataUriString.contains("/page/") || dataUriString.startsWith("playdramaflix://page")) {
            val pId = dataUri?.lastPathSegment?.toIntOrNull()
            if (pId != null) return ProcessedIntentResult(targetPageId = pId)
        }

        // গ) কমিউনিটি চ্যাট ইন্টেন্ট
        val isChatReply = intent.getBooleanExtra("EXTRA_OPEN_COMMUNITY_CHAT", false) ||
                intent.getStringExtra("type") == "chat_reply" ||
                intent.getStringExtra("type") == "community_chat" ||
                intent.getStringExtra("click_action") == "OPEN_COMMUNITY_CHAT" ||
                action == "OPEN_COMMUNITY_CHAT" ||
                dataUriString.contains("community_chat", ignoreCase = true)

        if (isChatReply) return ProcessedIntentResult(openCommunityChat = true)

        // ঘ) ভিআইপি স্ক্রিন ইন্টেন্ট
        val isVipAction = intent.getBooleanExtra("EXTRA_OPEN_VIP", false) ||
                intent.getStringExtra("type") == "vip_promo" ||
                intent.getStringExtra("type") == "vip_status_update" ||
                intent.getStringExtra("click_action") == "OPEN_VIP_CHECKOUT" ||
                intent.getStringExtra("click_action") == "OPEN_VIP_PRICING" ||
                intent.getStringExtra("click_action") == "OPEN_VIP_RENEW" ||
                action == "OPEN_VIP_CHECKOUT" ||
                action == "OPEN_VIP_PRICING" ||
                action == "OPEN_VIP_RENEW" ||
                action == "OPEN_VIP_ACTIVE"

        if (isVipAction) return ProcessedIntentResult(openVipScreen = true)

        // ঙ) অ্যাপ আপডেট ইন্টেন্ট
        val isCustomUpdate = intent.getBooleanExtra("EXTRA_OPEN_UPDATE_DIALOG", false) ||
                intent.getStringExtra("type") == "app_update" ||
                intent.getStringExtra("click_action") == "OPEN_APP_UPDATE" ||
                action == "OPEN_APP_UPDATE"

        if (isCustomUpdate) return ProcessedIntentResult(openUpdateDialog = true)

        // চ) শর্টস শনাক্তকরণ
        val isShortsFromExtra = intent.getBooleanExtra("IS_SHORTS", false) ||
                extras?.get("is_shorts")?.toString() == "1" ||
                extras?.get("is_shorts")?.toString() == "true" ||
                extras?.get("type")?.toString()?.equals("shorts", ignoreCase = true) == true ||
                extras?.get("content_type")?.toString()?.equals("shorts", ignoreCase = true) == true ||
                extras?.get("category")?.toString()?.contains("shorts", ignoreCase = true) == true

        // ছ) ড্রামা স্লাগ খোঁজা
        var foundSlug: String? = null
        if (extras != null) {
            val targetKeys = listOf(
                "slug", "content_slug", "post_slug", "target_slug",
                "drama_slug", "EXTRA_NOTIFICATION_SLUG", "id", "content_id", "drama_id"
            )
            for (key in targetKeys) {
                val value = extras.get(key)?.toString()
                val clean = extractCleanSlug(value)
                if (!clean.isNullOrBlank()) {
                    foundSlug = clean
                    break
                }
            }

            if (foundSlug.isNullOrBlank()) {
                val urlKeys = listOf("url", "link", "watch_url", "target_url")
                for (key in urlKeys) {
                    val value = extras.get(key)?.toString()
                    val clean = extractCleanSlug(value)
                    if (!clean.isNullOrBlank()) {
                        foundSlug = clean
                        break
                    }
                }
            }

            if (foundSlug.isNullOrBlank() && extras.containsKey("data")) {
                val dataString = extras.get("data")?.toString()
                foundSlug = extractCleanSlug(dataString)
            }

            if (foundSlug.isNullOrBlank() && extras.containsKey("title")) {
                val titleVal = extras.get("title")?.toString()
                if (!titleVal.isNullOrBlank()) {
                    foundSlug = titleVal.trim().lowercase().replace(Regex("[^a-zA-Z0-9\\s-]"), "").replace(Regex("\\s+"), "-")
                }
            }
        }

        if (foundSlug.isNullOrBlank() && dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "playdramaflix" || scheme == "dramaflix") {
                foundSlug = extractCleanSlug(dataUri.path) ?: extractCleanSlug(dataUri.host)
            } else if (scheme == "http" || scheme == "https") {
                foundSlug = extractCleanSlug(dataUri.toString())
            }
        }

        if (!foundSlug.isNullOrBlank()) {
            return ProcessedIntentResult(targetSlug = foundSlug, isShorts = isShortsFromExtra)
        }

        // জ) ব্রাউজার লিংক ইন্টেন্ট
        if (action == Intent.ACTION_VIEW && dataUri != null) {
            val scheme = dataUri.scheme?.lowercase() ?: ""
            if (scheme == "http" || scheme == "https") {
                val urlString = dataUri.toString()
                val isDirectMediaFile = urlString.endsWith(".mp4", true) ||
                        urlString.endsWith(".mkv", true) ||
                        urlString.endsWith(".mp3", true)

                if (!isDirectMediaFile) {
                    return ProcessedIntentResult(browserUrl = urlString)
                }
            }
        }

        // ঝ) বাইরের মিডিয়া ফাইল প্লেয়ার ইন্টেন্ট (ACTION_VIEW / ACTION_SEND)
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            val mediaUri: Uri? = if (action == Intent.ACTION_VIEW) {
                intent.data
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                } ?: intent.clipData?.getItemAt(0)?.uri
            }

            if (mediaUri != null && (mediaUri.scheme == "content" || mediaUri.scheme == "file")) {
                var fileName = "External Media"
                var fileSize = 0L
                var mimeType: String = intent.type ?: "video/*"

                try {
                    val resolvedType = context.contentResolver.getType(mediaUri)
                    if (!resolvedType.isNullOrBlank()) mimeType = resolvedType

                    context.contentResolver.query(mediaUri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) fileName = cursor.getString(nameIdx) ?: fileName
                            if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                } catch (_: Exception) {
                    fileName = mediaUri.lastPathSegment ?: "External Media"
                }

                val item = LocalVideoItem(
                    id = mediaUri.hashCode().toLong(),
                    title = fileName,
                    displayName = fileName,
                    durationMs = 0L,
                    sizeBytes = fileSize,
                    path = mediaUri.path ?: "",
                    contentUriString = mediaUri.toString(),
                    folderName = "External",
                    bucketId = "external_media",
                    dateAdded = System.currentTimeMillis() / 1000,
                    mimeType = mimeType
                )

                return ProcessedIntentResult(externalMediaItem = item)
            }
        }

        return ProcessedIntentResult()
    }
}
