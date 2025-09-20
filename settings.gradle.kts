@file:Suppress("UnstableApiUsage")

// settings.gradle.kts

pluginManagement {
    repositories {
        // Order matters: Google first for Android/Compose plugins
        google()
        mavenCentral()
        gradlePluginPortal()
        // maven("https://jitpack.io") // ← uncomment only if you really need JitPack
    }
}

dependencyResolutionManagement {
    // Force all modules to use these repos (prevents per-module repo drift)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        // Keep only what you need for Android/Compose/Kotlin
        google()
        mavenCentral()
        // maven("https://jitpack.io") // ← optional
    }
}

rootProject.name = "WallBase"
include(":app") // Ensure the module directory is actually named `app`