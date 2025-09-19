plugins {
    // Core plugins
    alias(libs.plugins.android.application) // Android Application Plugin
    alias(libs.plugins.kotlin.android)      // Kotlin Android Support
    alias(libs.plugins.kotlin.compose)      // Jetpack Compose Support
    alias(libs.plugins.ksp)                 // Kotlin Symbol Processing (for Room, etc.)
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.joshiminh.wallbase"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joshiminh.wallbase"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Instrumentation test runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Enable R8/ProGuard for release builds (disabled for now)
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Enable Compose
    buildFeatures { compose = true }

    // Java compatibility
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Kotlin JVM target
    kotlinOptions { jvmTarget = "21" }

    // Jetpack Compose options
    composeOptions {
        // Compose BOM handled in dependencies via platform()
        useLiveLiterals = false
    }

    // Packaging exclusions
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ---------- Jetpack Compose ----------
    implementation(platform(libs.androidx.compose.bom)) // Compose BOM for version alignment
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.pullrefresh)

    // Tooling & Testing for Compose
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // ---------- AndroidX Core ----------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Optional: DocumentFile API
    implementation(libs.androidx.documentfile)
    implementation(libs.play.services.auth)
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.jsoup)

    // ---------- Image Loading (Coil v3) ----------
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // ---------- Lifecycle helpers ----------
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ---------- Room (with KSP) ----------

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---------- Background Work ----------
    implementation(libs.androidx.work.runtime.ktx)

    // ---------- Testing ----------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}