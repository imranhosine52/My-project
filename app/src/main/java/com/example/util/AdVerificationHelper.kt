package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.service.AdTimerOverlayService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AdVerificationStatus(
    val isVerifying: Boolean = false,
    val targetEpisodeNumber: Int = 1,
    val totalRequiredSeconds: Int = 10,
    val remainingSeconds: Int = 0,
    val isFulfilled: Boolean = false,
    val isCancelledEarly: Boolean = false,
    val displayStatusText: String = "" // "Verifying… 10s", "Verified ✓"
)

object AdVerificationHelper {
    private const val NOTIF_CHANNEL_ID = "ad_timer_live_channel"
    private const val NOTIF_ID = 8881

    private val helperScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var verificationJob: Job? = null

    @Volatile
    private var adVisitStartTimeMs: Long = 0L

    @Volatile
    private var requiredDurationSeconds: Int = 10

    @Volatile
    private var targetEpisodeNumber: Int = 1

    private val _verificationState = MutableStateFlow(AdVerificationStatus())
    val verificationState: StateFlow<AdVerificationStatus> = _verificationState.asStateFlow()

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Ad Verification Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live ad verification timer countdown"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun showLiveNotification(context: Context, text: String, isDone: Boolean = false) {
        try {
            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(if (isDone) "Verified ✓" else "Sponsor Ad Verification")
                .setContentText(text)
                .setOngoing(!isDone)
                .setAutoCancel(isDone)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            manager.notify(NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    private fun cancelLiveNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    private fun triggerSuccessVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 📱 ফোনে অন্য অ্যাপের ওপর ড্র করার পারমিশন আছে কি না চেক
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * ⚙️ ১-ক্লিকে "Display over other apps" পারমিশন পেজ ওপেন করা
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            try {
                Toast.makeText(context, "বিজ্ঞাপনের ওপর টাইমার দেখতে পারমিশনটি On করে দিন", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * 🚀 ১. বিজ্ঞাপন শুরু: ফ্লোটিং পিল ও নোটিফিকেশন ব্যানার চালু
     */
    fun startAdVerification(
        context: Context,
        episodeNumber: Int,
        durationSeconds: Int,
        onComplete: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val safeDuration = durationSeconds.coerceAtLeast(3)

        adVisitStartTimeMs = System.currentTimeMillis()
        requiredDurationSeconds = safeDuration
        targetEpisodeNumber = episodeNumber

        verificationJob?.cancel()

        // ১. ফ্লোটিং ওভারলে পিল চালু (যদি পারমিশন থাকে)
        AdTimerOverlayService.start(appContext, safeDuration)

        // ২. ফলব্যাক লাইভ ব্যানার চালু (যাতে যেকোনো অবস্থাতেই মোবাইলের স্ক্রিনে কাউন্ট দেখা যায়)
        showLiveNotification(appContext, "Verifying… ${safeDuration}s")

        _verificationState.value = AdVerificationStatus(
            isVerifying = true,
            targetEpisodeNumber = episodeNumber,
            totalRequiredSeconds = safeDuration,
            remainingSeconds = safeDuration,
            isFulfilled = false,
            isCancelledEarly = false,
            displayStatusText = "Verifying… ${safeDuration}s"
        )

        verificationJob = helperScope.launch {
            var secondsLeft = safeDuration

            while (isActive && secondsLeft > 0) {
                val currentText = "Verifying… ${secondsLeft}s"
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = true,
                    remainingSeconds = secondsLeft,
                    displayStatusText = currentText,
                    isFulfilled = false
                )
                showLiveNotification(appContext, currentText)
                delay(1000L)
                secondsLeft--
            }

            // ০ সেকেন্ড হলে Verified ✓
            if (isActive) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = false,
                    remainingSeconds = 0,
                    isFulfilled = true,
                    displayStatusText = "Verified ✓"
                )
                showLiveNotification(appContext, "Verified ✓ Tap to return and watch!", isDone = true)
                triggerSuccessVibration(appContext)
                onComplete?.invoke()

                delay(2000L)
                cancelLiveNotification(appContext)
                if (_verificationState.value.isFulfilled) {
                    _verificationState.value = _verificationState.value.copy(displayStatusText = "")
                }
            }
        }
    }

    fun hasPendingSession(): Boolean = adVisitStartTimeMs > 0L

    /**
     * 🛡️ ২. ব্যবহারকারী অ্যাপে ফিরলে চেক করা
     */
    fun checkAndFulfillVisit(context: Context? = null): VerificationResult {
        if (adVisitStartTimeMs == 0L) {
            return VerificationResult(isSuccess = false, remainingSeconds = 0, message = "No active ad visit found.")
        }

        val elapsedMs = System.currentTimeMillis() - adVisitStartTimeMs
        val requiredMs = (requiredDurationSeconds - 1) * 1000L

        return if (elapsedMs >= requiredMs || _verificationState.value.isFulfilled) {
            verificationJob?.cancel()
            _verificationState.value = _verificationState.value.copy(
                isVerifying = false,
                isFulfilled = true,
                displayStatusText = "Verified ✓"
            )
            adVisitStartTimeMs = 0L

            context?.let {
                AdTimerOverlayService.stop(it)
                cancelLiveNotification(it)
            }

            VerificationResult(
                isSuccess = true,
                remainingSeconds = 0,
                message = "✓ Verified! Episode $targetEpisodeNumber unlocked."
            )
        } else {
            // ⚠️ সময় শেষ হওয়ার আগেই ব্যাক করেছে: টাইমার ও ওভারলে বাতিল
            val secondsLeft = (((requiredDurationSeconds * 1000L) - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
            cancelDueToEarlyExit(context)

            VerificationResult(
                isSuccess = false,
                remainingSeconds = secondsLeft,
                message = "⚠️ সময় শেষ হওয়ার আগেই ব্যাক করেছেন! আনলক করতে পুরো সময় অপেক্ষা করুন।"
            )
        }
    }

    fun cancelDueToEarlyExit(context: Context? = null) {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        _verificationState.value = AdVerificationStatus(
            isVerifying = false,
            isCancelledEarly = true,
            displayStatusText = ""
        )
        context?.let {
            AdTimerOverlayService.stop(it)
            cancelLiveNotification(it)
        }
    }

    fun resetVerification(context: Context? = null) {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        _verificationState.value = AdVerificationStatus()
        context?.let {
            AdTimerOverlayService.stop(it)
            cancelLiveNotification(it)
        }
    }
}

data class VerificationResult(
    val isSuccess: Boolean,
    val remainingSeconds: Int,
    val message: String
)
