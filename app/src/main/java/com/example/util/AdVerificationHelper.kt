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
    val isFulfilled: Boolean = false
)

object AdVerificationHelper {
    private const val TAG = "AdVerificationHelper"

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var verificationJob: Job? = null

    @Volatile
    private var adVisitStartTimeMs: Long = 0L

    @Volatile
    private var requiredDurationSeconds: Int = 10

    @Volatile
    private var targetEpisodeNumber: Int = 1

    private val _verificationState = MutableStateFlow(AdVerificationStatus())
    val verificationState: StateFlow<AdVerificationStatus> = _verificationState.asStateFlow()

    private var activeToast: Toast? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun showLiveToast(context: Context, message: String) {
        mainHandler.post {
            try {
                if (activeToast == null) {
                    activeToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
                } else {
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
                    vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * 🚀 অ্যাড ভিজিট শুরু করার মেথড
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

            while (isActive && secondsLeft > 0) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = true,
                    remainingSeconds = secondsLeft,
                    isFulfilled = false
                )
                showLiveToast(appContext, "⏳ Verifying sponsor visit: ${secondsLeft}s...")
                delay(1000L)
                secondsLeft--
            }

            if (isActive) {
                _verificationState.value = _verificationState.value.copy(
                    isVerifying = false,
                    remainingSeconds = 0,
                    isFulfilled = true
                )
                showLiveToast(appContext, "🎉 Verification Complete! Return to app.")
                triggerSuccessVibration(appContext)
                onComplete?.invoke()
            }
        }
    }

    /**
     * 🎯 কোনো অ্যাক্টিভ ভেরিফিকেশন সেশন আছে কি না যাচাই
     */
    fun hasPendingSession(): Boolean = adVisitStartTimeMs > 0L

    /**
     * 🛡️ অ্যান্টি-চিট চেকার: ব্রাউজার থেকে অ্যাপে ফিরলে এটি ১০০% সফলভাবে আনলক করবে
     */
    fun checkAndFulfillVisit(): VerificationResult {
        if (adVisitStartTimeMs == 0L) {
            return VerificationResult(isSuccess = false, remainingSeconds = 0, message = "No active ad visit found.")
        }

        val elapsedMs = System.currentTimeMillis() - adVisitStartTimeMs
        val requiredMs = (requiredDurationSeconds - 1) * 1000L // ১ সেকেন্ডের নেটওয়ার্ক ল্যাগ মার্জিন

        return if (elapsedMs >= requiredMs || _verificationState.value.isFulfilled) {
            verificationJob?.cancel()
            _verificationState.value = _verificationState.value.copy(isVerifying = false, isFulfilled = true)
            adVisitStartTimeMs = 0L // সেশন সম্পন্ন

            mainHandler.post { activeToast?.cancel(); activeToast = null }

            VerificationResult(
                isSuccess = true,
                remainingSeconds = 0,
                message = "✓ Verification completed! Episode $targetEpisodeNumber unlocked."
            )
        } else {
            val secondsLeft = (((requiredDurationSeconds * 1000L) - elapsedMs) / 1000L).toInt().coerceAtLeast(1)
            VerificationResult(
                isSuccess = false,
                remainingSeconds = secondsLeft,
                message = "⚠️ Please browse the ad page for $secondsLeft more seconds to unlock!"
            )
        }
    }

    fun resetVerification(context: Context) {
        verificationJob?.cancel()
        verificationJob = null
        adVisitStartTimeMs = 0L
        requiredDurationSeconds = 10
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
