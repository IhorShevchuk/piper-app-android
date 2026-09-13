plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "dev.ihorshevchuk.piper.tts"
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
    // Module coordinates are substituted to the sibling piper-kotlin repo's
    // Gradle projects by the composite build in settings.gradle.kts.
    implementation("dev.ihorshevchuk.piper:piper-engine")
    implementation("dev.ihorshevchuk.piper:piper-utils")
    implementation("dev.ihorshevchuk.piper:piper-player")
    testImplementation("junit:junit:4.13.2")
}
