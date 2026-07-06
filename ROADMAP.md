# WallBase Development Roadmap

This document outlines a 7-stage roadmap for refactoring the existing architecture, addressing technical debt, and introducing new features and enhancements. 

Each stage includes an **Agent Prompt** that you can copy and paste to an AI coding assistant to automatically implement that stage.

---

## [x] Stage 1: Core Network & Token Management Refactoring
**Focus:** Technical Debt, Concurrency Safety

**Overview:** 
The manual Dependency Injection container (`ServiceLocator.kt`) currently mixes DI concerns with Reddit token fetching. Fetching tokens inside the OkHttp interceptor uses `runBlocking`, which is dangerous and can lead to thread starvation or UI blocking.

**Agent Prompt:**
> "Execute Stage 1 of the ROADMAP.md: Extract the `redditToken` state and `getRedditAccessToken()` logic from `ServiceLocator.kt` into a dedicated `RedditTokenManager` class. Refactor `RedditAuthService.getAccessToken` to return a synchronous Retrofit `Call` instead of a `suspend` function. Update the interceptor to use `.execute()` synchronously, completely removing `runBlocking`. Finally, update `ServiceLocator.kt` to instantiate and use the new manager."

---

## [x] Stage 2: Security & Configuration Management
**Focus:** Security, Code Hygiene

**Overview:**
API keys and secrets (like the Reddit Client ID) are hardcoded in the source files. This is a security risk if the repository becomes public and makes environment switching difficult.

**Agent Prompt:**
> "Execute Stage 2 of the ROADMAP.md: Guide me through moving the hardcoded `REDDIT_CLIENT_ID` out of `ServiceLocator.kt`. Show me how to define it securely in `local.properties` and expose it via the `build.gradle.kts` `BuildConfig` field. Once the Gradle setup is complete, refactor `RedditTokenManager` (or `ServiceLocator`) to read the key from `BuildConfig.REDDIT_CLIENT_ID`."

---

## [ ] Stage 3: Hilt Dependency Injection Setup & Network Migration
**Focus:** Architecture, Scalability

**Overview:**
`ServiceLocator.kt` is a "God Object" that will become difficult to maintain as the app grows. Introducing Hilt (Google's recommended DI framework) will handle lifecycle management, scoping, and improve testability.

**Agent Prompt:**
> "Execute Stage 3 of the ROADMAP.md: Add the necessary Gradle dependencies and plugins to implement Dagger Hilt in this project. Create a custom `Application` class with `@HiltAndroidApp` and update the Manifest. Then, create a `NetworkModule.kt` object with `@InstallIn(SingletonComponent::class)`. Migrate the instantiation of `Moshi`, `OkHttpClient`, and all Retrofit services from `ServiceLocator` into this new Hilt module using `@Provides`."

---

## [ ] Stage 4: Completing DI Migration & ServiceLocator Removal
**Focus:** Architecture, Clean Code

**Overview:**
Finish the Hilt migration by moving database and repository definitions out of `ServiceLocator` and completely removing the manual DI container from the project.

**Agent Prompt:**
> "Execute Stage 4 of the ROADMAP.md: Create a `DatabaseModule.kt` Hilt module to provide the `WallBaseDatabase` and its DAOs. Update all repository classes (`WallpaperRepository`, `SourceRepository`, etc.) to use `@Inject constructor(...)`. Migrate any remaining dependencies out of `ServiceLocator.kt`, then safely delete `ServiceLocator.kt`. Finally, review how to inject these into ViewModels using `@HiltViewModel`."

---

## [ ] Stage 5: Data Layer Enhancements & Infinite Scrolling
**Focus:** New Feature, Performance

**Overview:**
When fetching wallpapers from Unsplash, Reddit, and Wallhaven, loading massive lists into memory at once can cause lag. Introducing Paging 3 will allow for infinite scrolling and efficient memory usage.

**Agent Prompt:**
> "Execute Stage 5 of the ROADMAP.md: Integrate the AndroidX Paging 3 library. Refactor `WallpaperRepository` to return `Flow<PagingData<Wallpaper>>` for the various network sources (Reddit, Unsplash, Wallhaven). Update the UI layer to collect and display this paging data using a `LazyVerticalGrid` (if Compose) or `PagingDataAdapter` (if XML), implementing infinite scrolling."

---

## [ ] Stage 6: Advanced Background Automation
**Focus:** New Feature, User Experience

**Overview:**
The current `WallpaperRotationRepository` uses `WorkManager`. This can be enhanced to give users more granular control over when their wallpapers rotate to save battery and data.

**Agent Prompt:**
> "Execute Stage 6 of the ROADMAP.md: Enhance the wallpaper rotation feature using `WorkManager`. Add new user settings to the `SettingsRepository` that allow rotation to occur *only* on Wi-Fi, or *only* when the device is charging. Update the rotation `Worker` constraints to respect these new settings. Additionally, add support for setting distinct lock screen versus home screen wallpapers during the auto-rotation."

---

## [ ] Stage 7: UI/UX Modernization & Material You
**Focus:** UI Polish, Monet Theming

**Overview:**
A wallpaper app's aesthetic should be top-tier. Implement dynamic colors (Material You) so the app's UI elements extract and match the colors of the currently viewed or set wallpaper.

**Agent Prompt:**
> "Execute Stage 7 of the ROADMAP.md: Modernize the app's theming. Ensure the app uses Material 3. Implement dynamic color support (Monet) so the app's theme adapts to the user's system colors. Furthermore, implement a palette extraction feature (using the Android Palette API) on the wallpaper detail screen, so the UI dynamically shifts to match the dominant colors of the currently selected wallpaper image."
