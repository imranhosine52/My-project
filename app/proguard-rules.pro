# =========================================================================
# 🛡️ ProGuard & R8 Optimization Rules
# =========================================================================

-keepattributes SourceFile,LineNumberTable
-keepattributes JavascriptInterface
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# 🎮 1. Unity Ads SDK Rules
-keep class com.unity3d.ads.** { *; }
-keep interface com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }
-keep interface com.unity3d.services.** { *; }
-dontwarn com.unity3d.services.**
-dontwarn com.unity3d.ads.**

# ⚡ 2. Start.io (StartApp) SDK Rules
-keep class com.startapp.** { *; }
-keep class com.startapp.sdk.adsbase.** { *; }
-dontwarn com.startapp.**

# 🌐 3. Networking (Retrofit, OkHttp, Moshi)
-keep class com.squareup.moshi.** { *; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# 📺 4. Media3 ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 🗄️ 5. Room Database
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
