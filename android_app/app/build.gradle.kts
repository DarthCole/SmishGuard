/*
 * build.gradle.kts (Module: app)
 * --------------------------------
 * This is the APP-LEVEL build file. It configures:
 *   1. Which plugins this module uses
 *   2. Android-specific settings (SDK versions, build types)
 *   3. Dependencies (libraries the app needs)
 *
 * KEY CONCEPTS:
 *   - compileSdk: The SDK version used to COMPILE your code (use latest for newest APIs)
 *   - minSdk: The LOWEST Android version your app supports (28 = Android 9 Pie)
 *   - targetSdk: The SDK version your app is DESIGNED and TESTED for
 *   - implementation: A dependency only this module can see
 *   - kapt: Kotlin Annotation Processing Tool — generates code at compile time (used by Room)
 */

plugins {
    id("com.android.application")        // This module produces an APK (not a library)
    id("org.jetbrains.kotlin.android")   // Enable Kotlin for Android
    id("org.jetbrains.kotlin.kapt")      // Enable annotation processing (needed by Room DB)
}

android {
    // Unique namespace for generated R class and BuildConfig
    namespace = "com.smishguard.app"

    // Use the latest stable SDK for compilation (access to newest APIs)
    compileSdk = 34

    defaultConfig {
        // The unique ID for your app on the Play Store and on the device
        applicationId = "com.smishguard.app"

        // Minimum Android version: 27 = Android 8.1 Oreo (your test device)
        minSdk = 27

        // Target SDK: tells Android to apply behavior changes up to this version
        targetSdk = 34

        // App version tracking (increment on each release)
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Debug build: used during development
        getByName("debug") {
            // 'isDebuggable = true' is the default for debug builds
            // Adds "D" suffix to app ID so debug and release can coexist on same device
            applicationIdSuffix = ".debug"
        }

        // Release build: used for distribution
        getByName("release") {
            // Enable code shrinking, obfuscation, and optimization
            isMinifyEnabled = true
            // Enable resource shrinking (removes unused resources)
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Use Java 17 bytecode (required by AGP 8.x)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Tell the Kotlin compiler to also target JVM 17
    kotlinOptions {
        jvmTarget = "17"
    }

    // Enable ViewBinding — generates type-safe classes to access XML views
    // Instead of findViewById(R.id.myButton), you write binding.myButton
    buildFeatures {
        viewBinding = true
        buildConfig = true   // Generate BuildConfig class with version info etc.
    }

    // Don't compress TFLite model files — required for memory-mapping
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    // ── AndroidX Core ──────────────────────────────────────────────
    // Core Kotlin extensions for Android (provides extension functions for Context, etc.)
    implementation("androidx.core:core-ktx:1.12.0")
    // AppCompat: backward-compatible versions of Android UI components
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Material Design components (buttons, cards, switches, etc.)
    implementation("com.google.android.material:material:1.11.0")
    // ConstraintLayout: flexible layout manager for complex UIs
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // SwipeRefreshLayout: pull-to-refresh gesture support
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // ── Navigation ─────────────────────────────────────────────────
    // Jetpack Navigation: handles Fragment transitions and back stack
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // ── Lifecycle (MVVM) ───────────────────────────────────────────
    // ViewModel: survives configuration changes (e.g., screen rotation)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    // LiveData: observable data holder — UI observes it and updates automatically
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    // Lifecycle runtime for coroutine support
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Service support for lifecycle-aware components
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")

    // ── Room Database ──────────────────────────────────────────────
    // Room: SQLite abstraction layer — write SQL with Kotlin data classes
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")       // Coroutine support for Room
    kapt("androidx.room:room-compiler:2.6.1")             // Generates DB code at compile time

    // ── Security ───────────────────────────────────────────────────
    // EncryptedSharedPreferences: stores key-value data with AES encryption
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // ── Coroutines ─────────────────────────────────────────────────
    // Kotlin Coroutines: lightweight threads for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── TensorFlow Lite (ML) ───────────────────────────────────────
    // On-device machine learning inference engine
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // ── Testing ────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
