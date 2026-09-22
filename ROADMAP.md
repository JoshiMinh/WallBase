# WallBase — Development Roadmap

- **Declarative / Script Scraper Engine**: For community extensibility, use a declarative JSON/XPath parser or lightweight JavaScript scraper engine (CloudStream-style) instead of APK extensions (Mihon-style). Allows users to import custom source rules or repository URLs without APK installation friction.

---

## Signed Release Build Guide

Steps to produce a properly signed Release APK that can be sideloaded on Android without security blocks.

### Step 1 — Generate a Release Keystore

Run in your terminal (requires JDK):

```powershell
keytool -genkeypair -v -keystore release.jks -alias wallbase -keyalg RSA -keysize 2048 -validity 10000
```

Move the generated `release.jks` into the `app/` folder.

### Step 2 — Store Keystore Secrets Securely

Create `keystore.properties` in the project root and add it to `.gitignore`:

```properties
RELEASE_STORE_FILE=../release.jks
RELEASE_STORE_PASSWORD=your_keystore_password
RELEASE_KEY_ALIAS=wallbase
RELEASE_KEY_PASSWORD=your_key_password
```

### Step 3 — Configure Signing in `app/build.gradle.kts`

```kotlin
android {
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(keystorePropertiesFile.inputStream())
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("RELEASE_STORE_FILE"))
                storePassword = keystoreProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### Step 4 — Build the Release APK

```powershell
# Windows
.\gradlew assembleRelease

# macOS / Linux
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

### Step 5 — Install on Device (Without Being Blocked)

- **Enable "Install Unknown Apps"** for your File Manager or browser in Settings → Apps → Special app access → Install unknown apps.
- **Play Protect warning**: Tap **More details → Install anyway** (new certificate not yet indexed in Google's database).
- **ADB install** (bypasses manual prompts entirely):

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
```