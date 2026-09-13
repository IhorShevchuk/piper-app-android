plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "dev.ihorshevchuk.piper.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ihorshevchuk.piper.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // System TTS service module (this repo).
    implementation(project(":tts-service"))
    // Voice catalog + download manager + voice list UI (this repo).
    implementation(project(":voice-download"))
    // Resolved to the sibling piper-kotlin repo via the composite build
    // in settings.gradle.kts (no publishing step needed).
    implementation("dev.ihorshevchuk.piper:piper-engine")
    implementation("dev.ihorshevchuk.piper:piper-utils")
    implementation("dev.ihorshevchuk.piper:piper-player")
    // Activity Result API (voice file import).
    implementation("androidx.activity:activity:1.9.3")
    // FileProvider for sharing exported WAV samples.
    implementation("androidx.core:core:1.13.1")

    testImplementation("junit:junit:4.13.2")
}
