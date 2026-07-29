plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.reddoor3.forexdial"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reddoor3.forexdial"   // same as companion-app — required for pairing
        minSdk = 30                                  // Wear OS 3 minimum
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    // Wear OS Watch Face
    implementation("androidx.wear.watchface:watchface:1.2.1")

    // Wear OS Complications (watch-side provider)
    implementation("androidx.wear.watchface:watchface-complications-data:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Wearable Data Layer (receives data from phone)
    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // Coroutines + Tasks bridge
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Wear OS core
    compileOnly("com.google.android.wearable:wearable:2.9.0")
    implementation("androidx.wear:wear:1.3.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
