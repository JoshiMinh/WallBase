@file:Suppress("UnstableApiUsage", "DEPRECATION")

import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    namespace = "com.joshiminh.wallbase"
    compileSdk = 36
    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.joshiminh.wallbase"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "6.5"
    }

    signingConfigs {
        create("release") {
            val signingPropsFile = rootProject.file("signing.properties")
            val properties = Properties()
            if (signingPropsFile.exists()) {
                signingPropsFile.inputStream().use { properties.load(it) }
            }

            val storeFilePath = properties.getProperty("KEYSTORE_PATH")
                ?: System.getenv("KEYSTORE_PATH")
                ?: "wallbase-release.jks"
            val keystoreFile = rootProject.file(storeFilePath)

            val storePass = properties.getProperty("KEYSTORE_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAl = properties.getProperty("KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS")
            val keyPass = properties.getProperty("KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")

            if (keystoreFile.exists() && !storePass.isNullOrBlank() && !keyAl.isNullOrBlank() && !keyPass.isNullOrBlank()) {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = keyAl
                keyPassword = keyPass
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        @Suppress("DEPRECATION")
        jvmTarget = "21"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    debugImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.palette)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.jsoup)

    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt)
    kapt(libs.hilt.compiler)

    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Unit Tests
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

val generateExtensionRepo = tasks.register("generateExtensionRepo") {
    group = "extensions"
    description = "Scans all extensions/*.json files and generates extensions/repo.json"

    val extensionsDir = rootProject.file("extensions")
    val repoFile = File(extensionsDir, "repo.json")

    inputs.dir(extensionsDir)
    outputs.file(repoFile)

    doLast {
        val slurper = groovy.json.JsonSlurper()
        val manifestFiles = extensionsDir.listFiles { _, name -> name.endsWith(".json") && name != "repo.json" }
            ?.sortedBy { it.name }
            ?: emptyList()

        val sources = manifestFiles.mapNotNull { file ->
            try {
                @Suppress("UNCHECKED_CAST")
                val json = slurper.parse(file) as? Map<String, Any?> ?: return@mapNotNull null
                val id = json["id"]?.toString() ?: file.nameWithoutExtension
                val name = json["name"]?.toString() ?: id
                val version = json["version"]?.toString() ?: "1.0.0"
                val versionCode = (json["versionCode"] as? Number)?.toInt() ?: 1
                val iconUrl = json["iconUrl"]?.toString()
                val description = json["description"]?.toString() ?: "$name Wallpapers"
                val author = json["author"]?.toString() ?: "JoshiMinh"
                val isNsfw = json["isNsfw"] as? Boolean ?: false

                mapOf(
                    "id" to id,
                    "name" to name,
                    "version" to version,
                    "versionCode" to versionCode,
                    "iconUrl" to iconUrl,
                    "description" to description,
                    "author" to author,
                    "manifestUrl" to "https://raw.githubusercontent.com/JoshiMinh/WallBase/main/extensions/${file.name}",
                    "isNsfw" to isNsfw
                )
            } catch (e: Exception) {
                logger.warn("Skipping malformed extension manifest: ${file.name}: ${e.message}")
                null
            }
        }

        val repoData = mapOf(
            "name" to "WallBase Official Extensions Repository",
            "author" to "JoshiMinh",
            "website" to "https://github.com/JoshiMinh/WallBase",
            "description" to "Official community declarative scraper extensions and source definitions for WallBase.",
            "version" to 1,
            "sources" to sources
        )

        val jsonOutput = groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(repoData))
        repoFile.writeText(jsonOutput + "\n")
        println("Generated extensions/repo.json with ${sources.size} extensions.")
    }
}

val syncRootExtensions = tasks.register<Copy>("syncRootExtensions") {
    dependsOn(generateExtensionRepo)
    from(rootProject.file("extensions"))
    into(file("src/main/assets/extensions"))
}

tasks.matching { it.name.startsWith("generate") && it.name.contains("Assets") || it.name == "preBuild" }.configureEach {
    dependsOn(syncRootExtensions)
}
