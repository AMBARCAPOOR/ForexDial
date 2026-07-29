// ForexDial Terminal — Watch Face Format module  v1.1  07_28_2026
// RESOURCE ONLY. No Kotlin/Java may ever be added here: Watch Face Format
// requires android:hasCode="false" and the APK must contain NO classes.dex.
// (Verified: the working WFF face on this watch has zero dex entries.)
// Complication data lives in :wear-app.

plugins {
    id("com.android.application")
}

android {
    namespace = "com.reddoor3.forexdial.face"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reddoor3.forexdial.face"
        minSdk = 33        // Wear OS 4 — minimum for Watch Face Format v1
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Every one of these otherwise emits generated Java, which becomes dex
    // and disqualifies the bundle as a Watch Face Format package.
    buildFeatures {
        buildConfig = false
        resValues = false
        aidl = false
        renderScript = false
        shaders = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
