import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Read secrets from local.properties (gitignored) rather than hardcoding them
// in source - the Finnhub key was hardcoded in Constants.kt and IS in git
// history as a result. Not repeating that mistake for the Twelve Data key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}

android {
    namespace = "com.reddoor3.forexdial"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reddoor3.forexdial"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TWELVE_DATA_API_KEY",
            "\"${localProps.getProperty("twelveDataApiKey", "")}\"")
        // Moved out of Constants.kt 2026-08-05. It had been hardcoded in
        // source and so is in git history - treat the old value as burned
        // and rotate it. Both keys now default to "" when local.properties
        // is absent, which is what makes a source-only release safe to
        // publish: a fresh clone builds with NO key baked in, so whoever
        // builds it must supply their own.
        buildConfigField("String", "FINNHUB_API_KEY",
            "\"${localProps.getProperty("finnhubApiKey", "")}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Wear OS Complications (phone-side provider)
    implementation("androidx.wear.watchface:watchface-complications-data:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // WorkManager (for yield spread + sunrise/sunset background jobs)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines + Tasks bridge (.await() on DataClient calls)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Wearable Data Layer
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
