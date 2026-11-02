package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdVerificationStatus(
    val isVerifying: Boolean = false,
    val targetEpisodeNumber: Int = 1,
    val totalRequiredSeconds: Int = 4,
    val remainingSeconds: Int = 0,
    val isFulfilled: Boolean = false
)

object AdVerificationHelper {
    private const val TAG = "AdVerificationHelper"
    private const val CHANNEL_ID = "ad_verification_channel"
    private const val CHANNEL_NAME = "Ad Visit Verification"
    private const val NOTIF_ID_VERIFICATION = 9997

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var verificationJob: Job? = null

    @Volatile
    private var adVisitStartTimeMs: Long = 0L

    @Volatile
    private var requiredDurationSeconds: Int = 4

    @Volatile
    private var targetEpisodeNumber: Int = 1

    private val _verificationState = MutableStateFlow(AdVerificationStatus())
    val verificationState: StateFlow<AdVerificationStatus> = _verificationState.asStateFlow()

    private fun ensureChannelCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows live countdown and completion alerts when visiting sponsor ads."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(soundUri, audioAttributes)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 🚀 ১. ব্রাউজারে যাওয়ার সময় অ্যান্টি-চিট ট্র্যাকার ও লাইভ নোটিফিকেশন কাউন্টডাউন শুরু করা
     */
    fun startAdVerification(
        context: Context,
        episodeNumber: Int,
        durationSeconds: Int,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        ensureChannelCreated(appContext)

        val safeDuration = durationSeconds.coerceAtLeast(3)
        adVisitStartTimeMs = System.currentTimeMillis()
        requiredDurationSeconds = safeDuration
        targetEpisodeNumber = episodeNumber

        verificationJob?.cancel()

        _verificationState.value = AdVerificationStatus(
            isVerifying = true,
            targetEpisodeNumber = episodeNumber,
            totalRequiredSeconds = safeDuration,
            remainingSeconds = safeDuration,
            isFulfilled = false
        )

        verificationJob = helperScope.launch {
            val notifManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            val intent = Intent(appContext, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("EXTRA_NOTIFICATION_EPISODE", episodeNumber)
            }

            val pendingIntent = PendingIntent.getActivity(
                appContext,
                NOTIF_ID_VERIFICATION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            var secondsLeft = safeDuration

            // প্রতি সেকেন্ডে ফোনের নোটিফিকেশন আপডেট হবে (লাইভ কাউন্টডাউন)
            while (isActive && secondsLeft > 0) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = true,
                    remainingSeconds = secondsLeft,
                    isFulfilled = false
                )

                try {
                    val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle("⏳ Verifying Sponsor Visit...")
                        .setContentText("Stay on the page: ${secondsLeft}s remaining to unlock Episode $episodeNumber")
                        .setProgress(safeDuration, safeDuration - secondsLeft, false)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .build()

                    notifManager?.notify(NOTIF_ID_VERIFICATION, notif)
                } catch (e: Exception) {
                    Log.w(TAG, "Notification post notice: ${e.message}")
                }

                delay(1000L)
                secondsLeft--
            }

            // ০ সেকেন্ডে পৌঁছালে: ইউজারকে ভাইব্রেশন ও সাউন্ড অ্যালার্ট দিয়ে ব্যাক করতে বলা
            if (isActive) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = false,
                    remainingSeconds = 0,
                    isFulfilled = true
                )

                try {
                    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val completeNotif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("🎉 Verification Complete!")
                        .setContentText("Tap here to return to PlayDramaFlix & watch Episode $episodeNumber.")
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText("🎉 Sponsor visit verified! Episode $episodeNumber is now unlocked. Tap here to start playing.")
                        )
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setAutoCancel(true)
                        .setSound(soundUri)
                        .setVibrate(longArrayOf(0, 300, 150, 300))
                        .setContentIntent(pendingIntent)
                        .build()

                    notifManager?.notify(NOTIF_ID_VERIFICATION, completeNotif)
                } catch (e: Exception) {
                    Log.e(TAG, "Complete notification failed: ${e.message}")
                }

                onComplete?.invoke()
            }
        }
    }

    /**
     * 🛡️ ২. কঠোর অ্যান্টি-চিট চেকার: ইউজার আসলেই পুরো সময় বিজ্ঞাপনে ছিল কি না যাচাই করা
     */
    fun checkAndFulfillVisit(): VerificationResult {
        if (adVisitStartTimeMs == 0L) {
            return VerificationResult(isSuccess = false, remainingSeconds = 0, message = "No active ad visit found.")
        }

        val elapsedMs = System.currentTimeMillis() - adVisitStartTimeMs
        val requiredMs = requiredDurationSeconds * 1000L

        return if (elapsedMs >= requiredMs) {
            // পুরো সময় পূরণ হয়েছে
            verificationJob?.cancel()
            _verificationState.value = _verificationState.value.copy(isVerifying = false, isFulfilled = true)
            VerificationResult(
                isSuccess = true,
                remainingSeconds = 0,
                message = "✓ Verification completed successfully! Unlocking Episode $targetEpisodeNumber."
            )
        } else {
            // ইউজার খুব দ্রুত ব্যাক করে চলে এসেছে (চিট প্রতিরোধ)
            val secondsLeft = ((requiredMs - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
            VerificationResult(
                isSuccess = false,
                remainingSeconds = secondsLeft,
                message = "⚠️ আপনি খুব দ্রুত ফিরে এসেছেন! বিজ্ঞাপনে আরও ${secondsLeft} সেকেন্ড সময় কাটাতে হবে।"
            )
        }
    }

    /**
     * 🧹 নোটিফিকেশন ও ট্র্যাকার রিসেট
     */
    fun resetVerification(context: Context) {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        requiredDurationSeconds = 4
        _verificationState.value = AdVerificationStatus()

        try {
            val notifManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notifManager?.cancel(NOTIF_ID_VERIFICATION)
        } catch (_: Exception) {}
    }
}

data class VerificationResult(
    val isSuccess: Boolean,
    val remainingSeconds: Int,
    val message: String
)
