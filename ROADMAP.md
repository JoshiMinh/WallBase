# WallBase — Improvement Roadmap

A prioritized list of suggested improvements across **UI**, **UX**, and **Logic / Architecture**. Items are grouped by theme and ordered from highest to lowest impact within each section.

---

## Table of Contents

1. [UI Improvements](#-ui-improvements)
2. [UX Improvements](#-ux-improvements)
3. [Logic & Architecture Improvements](#-logic--architecture-improvements)
4. [Priority Summary](#-priority-summary)

---

## 🎨 UI Improvements

### 1. Dark Mode & Dynamic Theming
- **What**: Add a proper dark theme and opt-in support for Android 12+ [dynamic color](https://m3.material.io/styles/color/dynamic-color/overview) (Material You / Monet).
- **Why**: The current theme is a fixed light theme. Dark mode is the top aesthetic expectation for a wallpaper app, and dynamic color lets the app theme itself from the user's current wallpaper — highly on-brand.
- **How**: Expose a `darkTheme` setting already scaffolded in `SettingsScreen`; wire it to `isSystemInDarkTheme()` or a stored preference. Add `dynamicColor = true` to `MaterialTheme`.

### 2. Animated Splash Screen
- **What**: Replace the blank white launch frame with an animated splash using the [Android 12 Splash Screen API](https://developer.android.com/guide/topics/ui/splash-screen).
- **Why**: Eliminates the jarring blank flash on cold-start and reinforces brand identity.
- **How**: Add `postSplashScreenTheme` in `themes.xml` and configure `SplashScreen.installSplashScreen()` in `MainActivity`.

### 3. Wallpaper Card Redesign
- **What**: Show a soft gradient scrim at the bottom of each `WallpaperCard` overlaying the resolution/source badge, and round card corners to 12 dp.
- **Why**: Text on image is currently hard to read on bright wallpapers; rounded corners align with Material3 card style.
- **How**: Replace the bare `Text` overlay with a `Box` containing a `Brush.verticalGradient` background.

### 4. Skeleton / Shimmer Loading States
- **What**: Replace the blank space while images load with shimmer placeholder cards that match the grid cell size.
- **Why**: Perceived performance improves significantly; users understand that content is loading rather than broken.
- **How**: Use Coil's `placeholder` parameter or the [Compose Shimmer](https://github.com/valentinilk/compose-shimmer) library.

### 5. Album Cover Collage
- **What**: When an album has no explicit cover selected, auto-generate a 2×2 collage from the four most recent wallpapers.
- **Why**: Empty or single-image covers make albums look unpolished; a collage mirrors the visual style of popular gallery apps.
- **How**: Draw a `Canvas` composable tiling four `AsyncImage` thumbnails in a 2×2 grid inside `AlbumCard`.

### 6. Bottom Sheet for Wallpaper Actions
- **What**: Consolidate the wallpaper action buttons (Set, Download, Add to Album, Share, Edit) into a `ModalBottomSheet` that slides up from `WallpaperDetailScreen`.
- **Why**: The current linear button layout wastes vertical space and requires long scrolling on smaller screens.
- **How**: Use `ModalBottomSheet` from `material3`; trigger it from a floating action button or a "…" overflow icon.

### 7. Fullscreen Immersive Preview
- **What**: Add a tap-to-toggle immersive mode in `WallpaperDetailScreen` that hides the system bars so the wallpaper fills the entire display.
- **Why**: Essential for judging how a wallpaper will actually look when applied.
- **How**: Call `WindowInsetsController.hide(WindowInsetsCompat.Type.systemBars())` on tap; restore on next tap.

### 8. Swipe-to-Dismiss on Detail Screen
- **What**: Allow the user to swipe down to dismiss `WallpaperDetailScreen` similar to native image viewers.
- **Why**: Swipe-to-dismiss is a well-established pattern in media viewers that feels natural and speeds up browsing.
- **How**: Combine `rememberDraggableState` with a vertical `Modifier.draggable` that scales/fades the image as the user drags.

### 9. Consistent Icon & Badge System
- **What**: Add small provider-logo badges (Unsplash, Reddit, Wallhaven, etc.) to each `WallpaperCard` corner so the source is immediately identifiable.
- **Why**: When browsing multiple sources users can distinguish origin at a glance without opening the detail screen.
- **How**: Map `sourceKey` to a small vector drawable; overlay it with a semi-transparent rounded background.

### 10. Empty State Illustrations
- **What**: Replace blank screens in Library (no albums/wallpapers) and Browse (no sources) with illustrations and friendly copy.
- **Why**: Empty states give users clear next-step guidance and make the app feel polished and intentional.
- **How**: Add dedicated `EmptyState` composable accepting a drawable, headline, and CTA button.

---

## 🧭 UX Improvements

### 1. Onboarding Flow
- **What**: Add a short 3-screen onboarding walkthrough shown once on first launch: (1) Welcome, (2) Add a Source, (3) Browse & Save.
- **Why**: New users currently land on an empty Library with no guidance on how to add content.
- **How**: Store an `onboarding_done` flag in `DataStore`; show `LandingScreen` conditionally before the main nav graph.

### 2. In-App Update Prompt
- **What**: Show a non-intrusive banner or dialog when a new version is available (the `UpdateRepository` already fetches release info).
- **Why**: The update check silently fails or is unnoticed; surfacing it in-app increases adoption of fixes and features.
- **How**: Observe `UpdateRepository` flow in `MainActivity`; show a `Snackbar` with "Update Available → Download" action.

### 3. Batch Operations UX
- **What**: Add a persistent action bar at the bottom of the screen when in multi-select mode (currently action buttons are in the top bar), including a "Select All" button.
- **Why**: A bottom action bar is ergonomically easier to reach on large phones; "Select All" reduces friction for bulk management.
- **How**: Add an `AnimatedVisibility` bottom bar in `LibraryScreen` that appears when `selectionMode == true`.

### 4. Undo / Snackbar for Destructive Actions
- **What**: After deleting a wallpaper or album, show a `Snackbar` with an "Undo" action for ~5 seconds before the deletion is committed to the database.
- **Why**: Accidental deletions are common and currently unrecoverable.
- **How**: Use a `Channel<UndoEvent>` in the ViewModel; delay the Room `delete` by 5 seconds and cancel if undo is triggered.

### 5. Wallpaper Search Across All Sources
- **What**: Add a global search bar in `BrowseScreen` that queries all configured sources simultaneously and shows unified results.
- **Why**: Users must currently browse each source individually, which is time-consuming.
- **How**: Fan out search queries via `combine` on all source `Flow`s; show a merged result list with source badges.

### 6. Filter & Tag System
- **What**: Allow tagging wallpapers with custom user-defined tags (e.g., "dark", "minimal", "nature") and filter the library by tag.
- **Why**: Albums provide coarse grouping; tags provide a fast cross-album filter for power users.
- **How**: Add a `tags` JSON column to `WallpaperEntity`; create a `TagFilterChip` row above the grid in `LibraryScreen`.

### 7. Rotation Schedule Quick Setup
- **What**: Add a "Quick Schedule" shortcut in `AlbumDetailScreen` that lets users set auto-rotation with a single tap (e.g., "Every hour").
- **Why**: Configuring rotation currently requires navigating to Settings → find album → configure interval — too many steps.
- **How**: Add a `ScheduleQuickSetDialog` composable triggered from the album's overflow menu.

### 8. Haptic Feedback
- **What**: Add subtle haptic feedback when entering selection mode (long-press) and when confirming wallpaper set.
- **Why**: Haptics confirm interactions and make the app feel tactile and responsive.
- **How**: Use `HapticFeedbackType.LongPress` and `HapticFeedbackType.Confirm` from Compose's `LocalHapticFeedback`.

### 9. Pull-to-Refresh in Browse
- **What**: Add pull-to-refresh to `SourceBrowseScreen` to reload the wallpaper feed without navigating away.
- **Why**: Users browsing a Reddit sub or Unsplash feed expect to be able to refresh for new content.
- **How**: Wrap the lazy grid in `PullToRefreshBox` from `material3` and invoke the ViewModel's `refresh()` method.

### 10. Keyboard & Accessibility
- **What**: Ensure all interactive elements have `contentDescription`s; add keyboard navigation for TV/foldable compatibility; support `CTRL+A` for "select all" in selection mode.
- **Why**: Many wallpaper apps are inaccessible to users relying on TalkBack or external keyboards.
- **How**: Audit all `Image`, `Icon`, and `IconButton` composables; add `semantics { contentDescription = … }` where missing.

---

## ⚙️ Logic & Architecture Improvements

### 1. Paging 3 Integration for Infinite Scroll
- **What**: Replace the current all-at-once load model with `Paging 3` (`PagingSource` + `LazyPagingItems`) in `SourceBrowseViewModel`.
- **Why**: Loading all items into memory at once causes ANR risk and excessive RAM usage on large feeds; pagination is essential for smooth infinite scroll.
- **How**: Implement a `PagingSource<Int, WallpaperItem>` per source; swap `LazyVerticalGrid` `items(list)` for `items(pagingItems)`.

### 2. Offline-First Cache Layer
- **What**: Cache remote wallpaper metadata in `WallpaperEntity` with a `cachedAt` timestamp and serve stale data while refreshing in the background.
- **Why**: The app requires an active connection to show any content; users on flaky networks see only errors.
- **How**: Add `cachedAt: Long` to `WallpaperEntity`; implement a stale-while-revalidate strategy in `WallpaperRepository`.

### 3. Structured Error Handling
- **What**: Replace ad-hoc `try/catch` blocks and silent failures with a sealed `AppError` hierarchy surfaced via a `Result<T>` wrapper through all repository functions.
- **Why**: Currently errors are swallowed or shown as generic toasts; users cannot distinguish network errors from API rate-limits from bad URLs.
- **How**: Define `sealed class AppError { Network, RateLimit, InvalidSource, Unauthorized, Unknown }`; map HTTP status codes in `HttpExtensions`.

### 4. Dependency Injection with Hilt
- **What**: Replace the manual `ServiceLocator` with Hilt DI (`@HiltViewModel`, `@Singleton`, `@Provides` modules).
- **Why**: Manual DI makes testing harder, increases boilerplate, and risks lifecycle mismanagement of repositories.
- **How**: Add `hilt-android` dependency; annotate `WallBaseApp` with `@HiltAndroidApp`; create `@Module` for database and repository bindings.

### 5. WorkManager Constraints for Rotation
- **What**: Add `NetworkType.NOT_REQUIRED`, battery-not-low, and idle constraints to `WallpaperRotationWorker`, and expose these toggles in Settings.
- **Why**: Background rotation currently runs without constraints and can drain battery or consume mobile data unexpectedly.
- **How**: Build `Constraints` in `WallpaperRotationRepository`; add "Rotation requires Wi-Fi" and "Require charging" toggles to `SettingsScreen`.

### 6. DataStore Migration from Shared Preferences
- **What**: Migrate any remaining `SharedPreferences` usage to `DataStore<Preferences>` for asynchronous, type-safe preferences.
- **Why**: `SharedPreferences` performs synchronous disk I/O on the main thread, a known source of jank.
- **How**: Use `PreferenceDataStoreFactory`; map existing preference keys to typed `Preferences.Key<T>` constants.

### 7. Image Download Queue with Progress
- **What**: Introduce a download queue (using WorkManager `DownloadWorker`) that tracks progress per wallpaper and exposes a live download indicator in the UI.
- **Why**: Downloads currently happen silently; users do not know if a download succeeded or failed.
- **How**: Enqueue one `DownloadWorker` per wallpaper; observe `WorkInfo.progress` via LiveData/Flow; show a `LinearProgressIndicator` on the card.

### 8. Source Plugin Architecture
- **What**: Define a common `WallpaperSourcePlugin` interface with standard `search(query)`, `browse(page)`, `resolve(url)` methods, and move each source (`UnsplashService`, `WallhavenService`, etc.) behind that interface.
- **Why**: Adding a new source currently requires touching multiple files; a plugin model lets sources be added/removed without core changes.
- **How**: Create `interface WallpaperSourcePlugin`; wrap each service in an adapter; register plugins via a `SourcePluginRegistry`.

### 9. Room Schema Version Management & Migrations
- **What**: Write explicit `Migration` objects for each schema version bump instead of relying on `fallbackToDestructiveMigration`.
- **Why**: Destructive migration silently deletes all user data on app update — a catastrophic data-loss bug.
- **How**: For each version pair, write a `Migration(from, to)` in a `Migrations.kt` file; register all in `Room.databaseBuilder`.

### 10. Unit & Integration Tests
- **What**: Add unit tests for all `Repository` classes using `kotlinx-coroutines-test` and in-memory Room databases; add UI tests for the three main screens using `ComposeTestRule`.
- **Why**: There are currently no automated tests; refactoring or adding features risks regressions with no safety net.
- **How**: Create `app/src/test` and `app/src/androidTest` directories; start with `WallpaperRepositoryTest`, `LibraryRepositoryTest`, and `LibraryScreenTest`.

---

## 📋 Priority Summary

| # | Item | Area | Impact | Effort |
|---|------|------|--------|--------|
| 1 | Dark Mode & Dynamic Theming | UI | 🔴 High | 🟢 Low |
| 2 | Undo for Destructive Actions | UX | 🔴 High | 🟢 Low |
| 3 | Room Migration (no destructive fallback) | Logic | 🔴 High | 🟡 Medium |
| 4 | Paging 3 Infinite Scroll | Logic | 🔴 High | 🟡 Medium |
| 5 | Skeleton / Shimmer Loading | UI | 🟠 Medium | 🟢 Low |
| 6 | Structured Error Handling | Logic | 🟠 Medium | 🟡 Medium |
| 7 | Pull-to-Refresh in Browse | UX | 🟠 Medium | 🟢 Low |
| 8 | In-App Update Prompt | UX | 🟠 Medium | 🟢 Low |
| 9 | Fullscreen Immersive Preview | UI | 🟠 Medium | 🟢 Low |
| 10 | Onboarding Flow | UX | 🟠 Medium | 🟡 Medium |
| 11 | Bottom Sheet for Actions | UI | 🟡 Low-Med | 🟢 Low |
| 12 | Haptic Feedback | UX | 🟡 Low-Med | 🟢 Low |
| 13 | WorkManager Constraints | Logic | 🟡 Low-Med | 🟢 Low |
| 14 | Batch Operations Bottom Bar | UX | 🟡 Low-Med | 🟢 Low |
| 15 | DataStore Migration | Logic | 🟡 Low-Med | 🟡 Medium |
| 16 | Download Queue + Progress | Logic | 🟡 Low-Med | 🟡 Medium |
| 17 | Hilt DI | Logic | 🟢 Low | 🔴 High |
| 18 | Source Plugin Architecture | Logic | 🟢 Low | 🔴 High |
| 19 | Filter & Tag System | UX | 🟢 Low | 🔴 High |
| 20 | Unit & Integration Tests | Logic | 🟢 Long-term | 🔴 High |

> **Legend** — Impact: 🔴 High · 🟠 Medium · 🟡 Low-Medium · 🟢 Low/Long-term  
> Effort: 🟢 Low · 🟡 Medium · 🔴 High
