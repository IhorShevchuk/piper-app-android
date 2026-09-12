pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "piper-app-android"
include(":app", ":tts-service", ":voice-download")

// The Piper library lives in its own repo (twin of the iOS piper-objc
// package). Check it out next to this repo:
//
//   ~/workspace/piper-android      (library: engine, utils, player)
//   ~/workspace/piper-app-android  (this repo)
//
// ...and the composite build wires the module coordinates below to the
// library's Gradle modules. No publishing step needed for local builds.
includeBuild("../piper-android") {
    dependencySubstitution {
        substitute(module("dev.ihorshevchuk.piper:piper-engine")).using(project(":piper-engine"))
        substitute(module("dev.ihorshevchuk.piper:piper-utils")).using(project(":piper-utils"))
        substitute(module("dev.ihorshevchuk.piper:piper-player")).using(project(":piper-player"))
    }
}
