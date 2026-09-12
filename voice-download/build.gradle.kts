plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "dev.ihorshevchuk.piper.voicedownload"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    // TTS service module: VoicePrefs, FileVoiceStore, EspeakDataInstaller,
    // and the voice-list entry-point contract. Same direction as :app.
    implementation(project(":tts-service"))
    // Resolved to the sibling piper-android repo via the composite build
    // in settings.gradle.kts (no publishing step needed).
    implementation("dev.ihorshevchuk.piper:piper-engine")
    implementation("dev.ihorshevchuk.piper:piper-player")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    // Real org.json on the JVM: the parser uses the Android framework's
    // org.json at runtime, which is an unmocked stub under unit tests.
    testImplementation("org.json:json:20240303")
}
