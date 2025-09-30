// settings.gradle.kts
@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // maven("https://jitpack.io") // only if needed
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // maven("https://jitpack.io")
    }
}

rootProject.name = "WallBase"
include(":app")