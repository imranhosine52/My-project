package com.example.util

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
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

    // 🎯 লাইভ টোস্ট আপডেটার ভেরিয়েবল
    private var activeToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showLiveToast(context: Context, message: String) {
        mainHandler.post {
            try {
                if (activeToast == null) {
                    activeToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
                } else {
                    // একই টোস্টের টেক্সট আপডেট করবে, বারবার নতুন টোস্ট স্ক্রিনে জমবে না
                    activeToast?.setText(message)
                    activeToast?.duration = Toast.LENGTH_SHORT
                }
                activeToast?.show()
            } catch (_: Exception) {}
        }
    }

    private fun triggerSuccessVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 🚀 ১. ব্রাউজারে যাওয়ার সময় অ্যান্টি-চিট ট্র্যাকার ও লাইভ টোস্ট কাউন্টডাউন শুরু করা
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
            isFulfilled = false
        )

        verificationJob = helperScope.launch {
            var secondsLeft = safeDuration

            // প্রতি সেকেন্ডে স্ক্রিনের নিচে টোস্ট আপডেট হবে (লাইভ কাউন্টডাউন)
            while (isActive && secondsLeft > 0) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = true,
                    remainingSeconds = secondsLeft,
                    isFulfilled = false
                )

                // ⏳ লাইভ টোস্ট শো করা
                showLiveToast(appContext, "⏳ Verifying sponsor visit: ${secondsLeft}s...")

                delay(1000L)
                secondsLeft--
            }

            // ০ সেকেন্ডে পৌঁছালে: টোস্ট ও ভাইব্রেশন দিয়ে ব্যাক করতে বলা
            if (isActive) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = false,
                    remainingSeconds = 0,
                    isFulfilled = true
                )

                showLiveToast(appContext, "🎉 Verification Complete! Please return to app.")
                triggerSuccessVibration(appContext)

                onComplete?.invoke()
            }
        }
    }

    /**
     * 🛡️ ২. কঠোর অ্যান্টি-চিট চেকার (প্রতারণা রোধ)
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
            
            // 🎯 টোস্টটি মুছে ফেলা
            mainHandler.post { activeToast?.cancel(); activeToast = null }
            
            VerificationResult(
                isSuccess = true,
                remainingSeconds = 0,
                message = "✓ Verification completed! Unlocking Episode $targetEpisodeNumber."
            )
        } else {
            // ইউজার খুব দ্রুত ব্যাক করে চলে এসেছে (চিট প্রতিরোধ)
            val secondsLeft = ((requiredMs - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
            VerificationResult(
                isSuccess = false,
                remainingSeconds = secondsLeft,
                // 🎯 সম্পূর্ণ ইংরেজিতে মেসেজ
                message = "⚠️ You came back too fast! Please stay on the ad for $secondsLeft more seconds."
            )
        }
    }

    /**
     * 🧹 ট্র্যাকার রিসেট
     */
    fun resetVerification(context: Context) {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        requiredDurationSeconds = 4
        _verificationState.value = AdVerificationStatus()
        
        mainHandler.post {
            try {
                activeToast?.cancel()
                activeToast = null
            } catch (_: Exception) {}
        }
    }
}

data class VerificationResult(
    val isSuccess: Boolean,
    val remainingSeconds: Int,
    val message: String
)
