# Hilt Dependency Injection Setup & Network Migration

This plan outlines the steps to introduce Dagger Hilt for dependency injection and migrate the network-related components from the manual `ServiceLocator` to Hilt modules.

## User Review Required

> [!IMPORTANT]
> - **Gradle Version Compatibility**: Hilt 2.60.1 requires AGP 8.x+ and Kotlin 2.x. The project is already on AGP 9.2.1 and Kotlin 2.2.21, so this is safe.
> - **ServiceLocator Retention**: `ServiceLocator` will NOT be deleted in this stage (Stage 3). It will be kept as a bridge until Stage 4, but its internal instantiation logic for network components will be moved to Hilt.
> - **ViewModel Factories**: Existing `ViewModelProvider.Factory` implementations in `companion object`s will be removed in favor of `@HiltViewModel`.

## Proposed Changes

### Build Configuration

#### [libs.versions.toml](file:///D:/Projects/Mixed/WallBase/gradle/libs.versions.toml)
- Add Hilt versions and libraries.
- Add Hilt Gradle plugin.

#### [build.gradle.kts (Project)](file:///D:/Projects/Mixed/WallBase/build.gradle.kts)
- Add Hilt plugin to the plugins block.

#### [build.gradle.kts (App)](file:///D:/Projects/Mixed/WallBase/app/build.gradle.kts)
- Apply Hilt and Kapt plugins.
- Add Hilt dependencies.
- Enable `buildConfig` (already enabled).

---

### Application Class

#### [NEW] [WallBaseApplication.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/WallBaseApplication.kt)
- Create a new `Application` class annotated with `@HiltAndroidApp`.
- Move `ServiceLocator.initialize(this)` into `onCreate()`.

#### [AndroidManifest.xml](file:///D:/Projects/Mixed/WallBase/app/src/main/AndroidManifest.xml)
- Set `android:name=".WallBaseApplication"` in the `<application>` tag.

---

### Hilt Modules

#### [NEW] [NetworkModule.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/di/NetworkModule.kt)
- Provide `Moshi`.
- Provide `OkHttpClient` with User-Agent and Logging interceptors.
- Provide `RedditAuthService`, `RedditService`, `UnsplashService`, `WallhavenService`, and `UpdateService`.
- Provide `RedditTokenManager`.
- Provide `WebScraper` (as `JsoupWebScraper`).

---

### Refactoring Existing Components

#### [RedditTokenManager.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/util/network/RedditTokenManager.kt)
- Add `@Inject constructor` to the class.

#### [JsoupWebScraper.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/util/network/JsoupWebScraper.kt)
- Add `@Inject constructor` to the class.

#### [ServiceLocator.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/util/network/ServiceLocator.kt)
- Update to use `@EntryPoint` to fetch dependencies from Hilt for now, or simply keep it as is if we want to minimize churn before Stage 4.
- *Recommendation*: Since Stage 4 is specifically about removing `ServiceLocator`, I will keep `ServiceLocator` as it is for now, but ensure that Hilt is ready to take over.

---

### Activity & ViewModels

#### [MainActivity.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/MainActivity.kt)
- Annotate with `@AndroidEntryPoint`.

#### [SourcesViewModel.kt](file:///D:/Projects/Mixed/WallBase/app/src/main/java/com/joshiminh/wallbase/ui/viewmodel/SourcesViewModel.kt)
- Annotate with `@HiltViewModel`.
- Add `@Inject constructor`.
- Remove manual `Factory`.

---

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to verify compilation and Hilt code generation.
- Run existing unit tests (if any) via `./gradlew test`.

### Manual Verification
- Deploy the app and verify:
    - App starts without crashing.
    - Reddit, Unsplash, and Wallhaven browsing still works (verifies NetworkModule).
    - Subreddit search still works (verifies RedditTokenManager and RedditAuthService).
    - Wallpaper rotation still works (verifies ServiceLocator fallback).
