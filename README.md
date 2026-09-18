<div align="center">
  <img src="icon.png" alt="WallBase Icon" width="96"/>

  # WallBase

  **Elevate your Android aesthetic.**

  An open-source wallpaper discovery and management app for Android.  
  Find, organize, and personalize high-resolution wallpapers across curated online sources.

  <br/>

  [![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
  [![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-3DDC84.svg?logo=android)](https://www.android.com)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
  [![Jetpack Compose](https://img.shields.io/badge/Compose-Material_3-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
  [![Version](https://img.shields.io/badge/Version-6.1-pink.svg)](https://github.com/joshiminh/WallBase/releases)
</div>

---

## Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center" width="33%"><b>Library & Collections</b></td>
      <td align="center" width="33%"><b>Source Discovery</b></td>
      <td align="center" width="33%"><b>Settings & Theming</b></td>
    </tr>
    <tr>
      <td align="center">
        <img src="screenshots/library_preview.jpg" alt="Library Preview" width="100%"/>
      </td>
      <td align="center">
        <img src="screenshots/sources_preview.jpg" alt="Sources Preview" width="100%"/>
      </td>
      <td align="center">
        <img src="screenshots/settings_preview.jpg" alt="Settings Preview" width="100%"/>
      </td>
    </tr>
  </table>
</div>

---

## Features

- **Multi-Source Discovery**: Search and browse high-resolution wallpapers from multiple platforms including Reddit, Wallhaven, and Pinterest.
- **Personal Library & Custom Albums**: Organize favorite wallpapers into custom collections with offline caching and direct downloads.
- **Material You & Custom Theming**: Support for dynamic theming, pure AMOLED dark mode, and customizable accent colors.
- **One-Tap Wallpaper Setup**: Set wallpapers directly to Home Screen, Lock Screen, or both, with integrated palette extraction.
- **Biometric Security**: Protect saved collections and private albums with integrated biometric authentication.
- **Modern Performance**: Smooth edge-to-edge layouts, asynchronous image decoding, and efficient memory management.

---

## Tech Stack & Architecture

WallBase is engineered following modern Android development practices and Clean Architecture principles:

- **UI Framework**: 100% Jetpack Compose with Material 3 (Material You)
- **Architecture**: MVVM with Unidirectional Data Flow (UDF) and StateFlow
- **Dependency Injection**: Dagger Hilt
- **Persistence**: Room Database (SQLite) and Jetpack DataStore Preferences
- **Networking**: Retrofit 2, Moshi, OkHttp 4, and Jsoup
- **Image Pipeline**: Coil 3 with OkHttp caching
- **Minimum SDK**: Android 8.0 (API level 26)
- **Target SDK**: Android 16 (API level 36)

---

## Building from Source

### Prerequisites

- Android Studio (Ladybug / Meerkat or newer)
- JDK 21
- Android SDK 36

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/joshiminh/WallBase.git
   cd WallBase
   ```

2. (Optional) Configure Reddit API Credentials in `local.properties`:
   ```properties
   REDDIT_CLIENT_ID=your_client_id_here
   ```

3. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## Contributing

Contributions, bug reports, and feature suggestions are welcome. Please feel free to open an issue or submit a pull request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
