@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.joshiminh.wallbase"
    //noinspection GradleDependency
    compileSdk = 36
    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.joshiminh.wallbase"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        // Using Java 21 toolchain with Android
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        // coreLibraryDesugaringEnabled not needed for minSdk 26 unless you use newer java.util APIs that require it
    }

    kotlinOptions {
        @Suppress("DEPRECATION")
        jvmTarget = "21"
    }

    // No need to pin kotlinCompilerExtensionVersion when using recent AGP + Compose BOM.
    composeOptions {
        @Suppress("DEPRECATION")
        useLiveLiterals = false // NOTE: Deprecated and will be removed in AGP 9.0
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // ---------------- Compose (BOM aligns all androidx.compose.* versions) ----------------
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    // Tooling (debug-only)
    debugImplementation(libs.androidx.ui.tooling)

    // Compose UI tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // ---------------- AndroidX Core ----------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)

    // Optional
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.compose.animation)

    // ---------------- Networking / JSON ----------------
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.jsoup)

    // ---------------- Image Loading ----------------
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // ---------------- Room (annotation processing) ----------------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    //noinspection KaptUsageInsteadOfKsp
    kapt(libs.androidx.room.compiler)

    // ---------------- WorkManager ----------------
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.runtime.ktx)

    // ---------------- Testing ----------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Additional Compose utilities are already aligned by the BOM above.
}