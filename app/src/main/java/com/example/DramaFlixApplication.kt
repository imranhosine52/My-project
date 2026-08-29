package com.example

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import com.example.ads.StartIoAdManager
import com.example.data.repository.PlayDramaFlixRepository
import com.google.firebase.FirebaseApp

class DramaFlixApplication : Application() {
    val repository: PlayDramaFlixRepository by lazy {
        PlayDramaFlixRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        
        // Defensive WebView configuration to avoid multi-process directory locks or renderer crashes
        initSafeWebView()

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("DramaFlixApp", "FirebaseApp initialized successfully.")
            }
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Firebase initialization skipped or failed: ${e.message}")
        }

        // Initialize Start.io (StartApp) Ads SDK
        try {
            val isVip = repository.isUserVip()
            StartIoAdManager.init(this, isVip = isVip)
        } catch (e: Exception) {
            Log.w("DramaFlixApp", "Start.io Ads initialization failed: ${e.message}")
        }
    }

    private fun initSafeWebView() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val processName = Application.getProcessName()
                if (packageName != processName) {
                    WebView.setDataDirectorySuffix(processName)
                }
            }
        } catch (e: Throwable) {
            Log.w("DramaFlixApp", "WebView setup notice: ${e.message}")
        }
    }
}
