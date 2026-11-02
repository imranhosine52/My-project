package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.MainActivity
import kotlinx.coroutines.*

class AdTimerOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var timerTextView: TextView? = null
    private var progressBar: ProgressBar? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null

    companion object {
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val ACTION_STOP_OVERLAY = "action_stop_overlay"

        fun start(context: Context, durationSeconds: Int) {
            val intent = Intent(context, AdTimerOverlayService::class.java).apply {
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, AdTimerOverlayService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            removeOverlayAndStop()
            return START_NOT_STICKY
        }

        val duration = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 10) ?: 10

        // ওভারলে পারমিশন থাকলে স্ক্রিনের ওপর ফ্লোটিং পিল চালু হবে
        if (canDrawOverlay()) {
            showFloatingPill(duration)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun canDrawOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun showFloatingPill(durationSeconds: Int) {
        if (overlayView != null) {
            removeOverlayView()
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(72) // ক্রোম ব্রাউজারের নিচের বারের কিছুটা ওপরে সুন্দর মার্জিন
        }

        val pillLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))

            // 🎨 ডার্ক ফ্রস্টেড গ্লাস ব্যাকগ্রাউন্ড ও নিয়ন বর্ডার
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F2121622"))
                cornerRadius = dpToPx(24).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#4D00E5FF"))
            }

            // ক্লিক করলে সরাসরি ইউজারকে ক্রোম থেকে অ্যাপে ফিরিয়ে আনবে
            setOnClickListener {
                bringAppToFront()
            }
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
            val size = dpToPx(16)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(8)
            }
            indeterminateDrawable?.setTint(Color.parseColor("#00E5FF"))
        }

        timerTextView = TextView(this).apply {
            text = "Verifying… ${durationSeconds}s"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        pillLayout.addView(progressBar)
        pillLayout.addView(timerTextView)

        overlayView = pillLayout

        try {
            windowManager?.addView(overlayView, params)
            startRealTimeCountdown(durationSeconds)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun startRealTimeCountdown(totalSeconds: Int) {
        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            var secondsLeft = totalSeconds

            while (isActive && secondsLeft > 0) {
                timerTextView?.text = "Verifying… ${secondsLeft}s"
                delay(1000L)
                secondsLeft--
            }

            if (isActive) {
                // ০ সেকেন্ডে পৌঁছালে: গ্রিন স্টেট ও Verified ✓
                progressBar?.visibility = View.GONE
                timerTextView?.apply {
                    text = "Verified ✓ (Tap to return)"
                    setTextColor(Color.parseColor("#00E676"))
                }

                // পটভূমির বর্ডার সবুজ করা
                (overlayView as? LinearLayout)?.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F2102015"))
                    cornerRadius = dpToPx(24).toFloat()
                    setStroke(dpToPx(1), Color.parseColor("#00E676"))
                }

                // ২.৫ সেকেন্ড অপেক্ষা করে সার্ভিস বন্ধ হবে
                delay(2500L)
                removeOverlayAndStop()
            }
        }
    }

    private fun bringAppToFront() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun removeOverlayView() {
        try {
            if (overlayView != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (_: Exception) {}
    }

    private fun removeOverlayAndStop() {
        countdownJob?.cancel()
        removeOverlayView()
        stopSelf()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlayView()
    }
}
