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
    // Resolved to the sibling piper-android repo via the composite build
    // in settings.gradle.kts (no publishing step needed).
    implementation("dev.ihorshevchuk.piper:piper-engine")
    implementation("dev.ihorshevchuk.piper:piper-utils")
    implementation("dev.ihorshevchuk.piper:piper-player")
}
