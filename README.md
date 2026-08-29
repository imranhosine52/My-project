# 🎬 Play Drama Flix - Android Application

A high-performance Android streaming application built with Kotlin, Jetpack Compose, ExoPlayer/Media3, and Start.io Ads SDK.

---

## 🚀 GitHub Actions Auto APK Build

This repository includes a pre-configured GitHub Actions CI/CD workflow (`.github/workflows/build-apk.yml`) to automatically build and export ready-to-install Android APKs (`.apk`).

### 📦 How to Download APK from GitHub:
1. Push your code to GitHub (`main` or `master` branch).
2. Go to the **Actions** tab on your GitHub repository.
3. Click on the latest run under **"Build & Release Android APK"**.
4. Scroll down to the **Artifacts** section at the bottom of the summary page.
5. Click **`PlayDramaFlix-APKs`** to download your APKs (`PlayDramaFlix-debug.apk` & `PlayDramaFlix-release.apk`) directly to your phone or computer.

### 🕹️ Manual Build via GitHub UI (1-Click Dispatch):
1. Go to the **Actions** tab.
2. Select **"Build & Release Android APK"** from the left sidebar.
3. Click **"Run workflow"** and select the build type (`all`, `debug`, or `release`).
4. Wait ~2-3 minutes for the build to finish and download your artifact.

---

## 🛠️ Local Build Instructions

### Prerequisites:
- Java JDK 21 (or JDK 17+)
- Android SDK Platform 36 (minSdk 24, targetSdk 36)

### Commands:
```bash
# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```
APKs will be generated in `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

---

## ⚙️ Versions & Tech Stack
- **Kotlin:** 2.2.10
- **Android Gradle Plugin (AGP):** 9.1.1
- **Gradle:** 9.3.1
- **Jetpack Compose:** Compose BOM 2024.09.00
- **Media3 (ExoPlayer + HLS):** 1.5.0
- **Start.io Ads SDK:** 5.1.0
- **Room Database:** 2.7.0
- **Retrofit & Moshi:** 2.12.0 / 1.15.2
