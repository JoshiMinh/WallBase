<div align="center">
  <img src="icon.png" alt="WallBase Icon" width="96"/>

  <h1>WallBase</h1>

  <p><strong>Elevate your Android aesthetic.</strong></p>
  <p>An open-source wallpaper discovery and management engine built for the modern Android ecosystem. Find, save, and automate high-quality wallpapers from the web's most curated sources.</p>

  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?logo=android)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
</div>

---

## ✨ Features

- 🖼️ **Curated Sources:** Seamlessly browse wallpapers from Reddit, Pinterest, Wallhaven, and Danbooru.
- 🎨 **Advanced Theming:** Choose between Light, Dark, and true **AMOLED Black** modes.
- 🌈 **Custom Accents:** Personalize your experience with a curated spectrum of UI accent colors.
- 🔄 **Auto-Refresh:** Keep your home screen fresh with automated wallpaper rotation at set intervals.
- 🚀 **Modern Stack:** Built with **Jetpack Compose**, **Kotlin**, and **Material 3** for fluid performance.
- 🔒 **Privacy Focused:** Secure your personal library with integrated biometric authentication.

## 🛠️ Tech Stack

| **Category** | **Technology** |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Architecture** | MVVM + Clean Architecture |
| **Data Storage** | Jetpack DataStore & Room |
| **Image Loading** | Coil |

---

## 🚀 Getting Started

Follow these steps to build and run the application using the automated script or manual commands.

### Quick Start (Windows)

The project includes an automated script and environment configuration for Windows users.

1. **Configure Environment:**
   Review and edit the [.env](.env) file to ensure `DEVICE_ID` and SDK paths match your local setup.

2. **Run the Script:**
   Execute the [run.bat](run.bat) file from the root directory:

   ```cmd
   run.bat
   ```

   Choose from the options to build, install, or launch the app.

---

### Manual Installation Steps (Cross-Platform)

#### 1. Prepare the Device

Launch an emulator or connect a physical device via USB:

```powershell
# Verify device connection
adb devices
```

#### 2. Build and Install the App

Use the Gradle Wrapper (`gradlew`) to build and install the debug version:

```powershell
./gradlew.bat installDebug
```

*Note: On macOS/Linux, use `./gradlew installDebug`.*

#### 3. Launch the Application

Once installed, the app won't start automatically. Use `adb` to start the main activity:

```powershell
# Standard command (if adb is in your PATH)
adb shell am start -n com.joshiminh.wallbase/com.joshiminh.wallbase.MainActivity

# If adb is NOT in your PATH, use the full path to the Android SDK
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.joshiminh.wallbase/com.joshiminh.wallbase.MainActivity
```

## Troubleshooting

### ADB Path

If `adb` is not recognized, it is typically located at:

- **Windows:** `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`
- **macOS/Linux:** `~/Library/Android/sdk/platform-tools/adb`

### Build Failures

- Ensure your `JAVA_HOME` points to a compatible JDK.
- Running `./gradlew.bat clean` can often resolve transient build issues.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a pull request.

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
