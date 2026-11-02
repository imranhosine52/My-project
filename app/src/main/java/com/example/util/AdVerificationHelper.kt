package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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

    private fun triggerSuccessVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 🚀 ১. স্পনসর লিংকে ক্লিক করলে রিয়েল-টাইম লাইভ কাউন্টডাউন শুরু করা
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
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = true,
                    remainingSeconds = secondsLeft,
                    displayStatusText = "Verifying… ${secondsLeft}s",
                    isFulfilled = false
                )
                delay(1000L)
                secondsLeft--
            }

            // ০ সেকেন্ডে পৌঁছালে: "Verified ✓" প্রদর্শন ও ভাইব্রেশন
            if (isActive) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = false,
                    remainingSeconds = 0,
                    isFulfilled = true,
                    displayStatusText = "Verified ✓"
                )
                triggerSuccessVibration(appContext)
                onComplete?.invoke()

                // ১.২ সেকেন্ড পর ডায়নামিক লেয়ার স্মুথলি হাইড করা
                delay(1200L)
                if (_verificationState.value.isFulfilled) {
                    _verificationState.value = _verificationState.value.copy(displayStatusText = "")
                }
            }
        }
    }

    /**
     * 🔍 সেশন সক্রিয় আছে কি না যাচাই
     */
    fun hasPendingSession(): Boolean = adVisitStartTimeMs > 0L

    /**
     * 🛡️ ২. অ্যাপে ফেরার পর চেকার:
     * - পুরো সময় থাকলে: আনলক সফল
     * - সময় শেষ হওয়ার আগে ব্যাক করলে: টাইম কাউন্ট বন্ধ, রিসেট এবং মেসেজ প্রদান
     */
    fun checkAndFulfillVisit(): VerificationResult {
        if (adVisitStartTimeMs == 0L) {
            return VerificationResult(isSuccess = false, remainingSeconds = 0, message = "No active ad visit found.")
        }

        val elapsedMs = System.currentTimeMillis() - adVisitStartTimeMs
        val requiredMs = (requiredDurationSeconds - 1) * 1000L // ১ সেকেন্ডের মার্জিন

        return if (elapsedMs >= requiredMs || _verificationState.value.isFulfilled) {
            verificationJob?.cancel()
            _verificationState.value = _verificationState.value.copy(
                isVerifying = false,
                isFulfilled = true,
                displayStatusText = "Verified ✓"
            )
            adVisitStartTimeMs = 0L

            VerificationResult(
                isSuccess = true,
                remainingSeconds = 0,
                message = "✓ Verified! Episode $targetEpisodeNumber unlocked."
            )
        } else {
            // ⚠️ সময় শেষ হওয়ার আগেই ব্যাক করেছে: সম্পূর্ণ রিসেট
            val secondsLeft = (((requiredDurationSeconds * 1000L) - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
            cancelDueToEarlyExit()

            VerificationResult(
                isSuccess = false,
                remainingSeconds = secondsLeft,
                message = "⚠️ সময় শেষ হওয়ার আগেই ব্যাক করেছেন! আনলক করতে পুরো সময় অপেক্ষা করুন।"
            )
        }
    }

    /**
     * 🚫 আগে ব্যাক করলে টাইমার বন্ধ এবং সবকিছু প্রথম থেকে শুরু করার জন্য রিসেট
     */
    fun cancelDueToEarlyExit() {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        _verificationState.value = AdVerificationStatus(
            isVerifying = false,
            isCancelledEarly = true,
            displayStatusText = ""
        )
    }

    /**
     * 🧹 ফুল রিসেট
     */
    fun resetVerification() {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        _verificationState.value = AdVerificationStatus()
    }
}

data class VerificationResult(
    val isSuccess: Boolean,
    val remainingSeconds: Int,
    val message: String
)
