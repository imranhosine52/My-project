package com.example.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
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
    val displayStatusText: String = ""
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

    // 🎯 স্ক্রিনশটের হুবহু অ্যান্ড্রয়েড সিস্টেম পিল কন্ট্রোলার (কোনো স্পেশাল পারমিশন ছাড়া)
    private var activeToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showSystemPill(context: Context, message: String) {
        mainHandler.post {
            try {
                activeToast?.cancel() // আগেরটি ক্যানসেল করে সাথে সাথে নতুন সেকেন্ডের পিল প্রদর্শন
                activeToast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT)
                activeToast?.show()
            } catch (_: Exception) {}
        }
    }

    private fun cancelSystemPill() {
        mainHandler.post {
            try {
                activeToast?.cancel()
                activeToast = null
            } catch (_: Exception) {}
        }
    }

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
     * 🚀 ১. স্পনসর লিংকে ক্লিক করলে কোনো পারমিশন ছাড়াই স্ক্রিনের নিচে লাইভ পিল কাউন্টডাউন শুরু
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

        // প্রথমবার ক্লিক করলেই নিচে স্ক্রিনশটের মতো পিল দেখানো
        showSystemPill(appContext, "Verifying… ${safeDuration}s")

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

                // প্রতি সেকেন্ডে নিচে রিয়েল-টাইমে কাউন্টডাউন আপডেট
                showSystemPill(appContext, currentText)

                delay(1000L)
                secondsLeft--
            }

            // ০ সেকেন্ডে পৌঁছালে: Verified ✓ প্রদর্শন ও ভাইব্রেশন
            if (isActive) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = false,
                    remainingSeconds = 0,
                    isFulfilled = true,
                    displayStatusText = "Verified ✓"
                )
                showSystemPill(appContext, "Verified ✓")
                triggerSuccessVibration(appContext)
                onComplete?.invoke()

                delay(1500L)
                cancelSystemPill()
            }
        }
    }

    fun hasPendingSession(): Boolean = adVisitStartTimeMs > 0L

    /**
     * 🛡️ ২. ব্যবহারকারী অ্যাপে ফেরার পর চেকার:
     * - সময় শেষ হওয়ার আগে ফিরলে: টাইম কাউন্ট বন্ধ, রিসেট এবং স্ক্রিনশটের মতো সতর্কবার্তা পিল
     * - পুরো সময় থাকলে: আনলক সফল
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

            context?.let { cancelSystemPill() }

            VerificationResult(
                isSuccess = true,
                remainingSeconds = 0,
                message = "✓ Verified! Episode $targetEpisodeNumber unlocked."
            )
        } else {
            // ⚠️ সময় শেষ হওয়ার আগেই ব্যাক করেছে: সাথে সাথে টাইমার বন্ধ ও রিসেট
            val secondsLeft = (((requiredDurationSeconds * 1000L) - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
            cancelDueToEarlyExit(context)

            val warningMessage = "⚠️ সময় শেষ হওয়ার আগেই ব্যাক করেছেন! আনলক করতে পুরো সময় অপেক্ষা করুন।"
            context?.let { showSystemPill(it, warningMessage) }

            VerificationResult(
                isSuccess = false,
                remainingSeconds = secondsLeft,
                message = warningMessage
            )
        }
    }

    /**
     * 🚫 আগে ব্যাক করলে টাইমার বন্ধ এবং সবকিছু প্রথম থেকে শুরু করার জন্য রিসেট
     */
    fun cancelDueToEarlyExit(context: Context? = null) {
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
    fun resetVerification(context: Context? = null) {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        _verificationState.value = AdVerificationStatus()
        cancelSystemPill()
    }
}

data class VerificationResult(
    val isSuccess: Boolean,
    val remainingSeconds: Int,
    val message: String
)
