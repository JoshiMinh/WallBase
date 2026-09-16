---
name: android-performance
description: Expert guidelines for optimizing Android and Jetpack Compose performance, memory usage, Coil image caching, background WorkManager tasks, and coroutine concurrency in WallBase.
---

# Android Performance Optimization Specialist

This skill provides performance rules, profiling workflows, and optimization best practices for WallBase.

## 1. Jetpack Compose Performance

- **Recomposition Optimization**:
  - Use `remember` with precise keys to cache expensive calculations.
  - Wrap derived values with `remember { derivedStateOf { ... } }` when observing high-frequency changes (e.g. scroll offsets).
  - Prefer immutable data classes (`@Immutable` / `@Stable`) for UI state objects passed to composables.
  - Avoid creating unmemoized lambda instances or allocations directly inside the composable body; pass method references or hoisted event callbacks.
  - Use `key(...)` in `LazyVerticalGrid` / `LazyColumn` items with stable unique IDs (`wallpaper.id`, `album.id`).

## 2. High-Resolution Wallpaper Image Pipeline (Coil 3)

- Wallpapers are high-resolution media (often 4K/8K).
- Configure Coil `AsyncImage` with:
  - Appropriate target size / downsampling (`crossfade(true)`, `scale(Scale.FIT)` or `Scale.CROP`).
  - Memory and disk cache policies (`CachePolicy.ENABLED`).
  - Low-resolution placeholder / blurhash thumbnail while full-resolution wallpaper loads.
- Clean up bitmap references when leaving detail screens to avoid high memory spikes.

## 3. Background Work & Battery Optimization (WorkManager)

- For auto-rotation workers (`WallpaperRotationWorker`):
  - Enforce battery-not-low constraints (`NetworkType.UNMETERED` when Wi-Fi only is selected, `requiresCharging` when charging only is set).
  - Avoid duplicate worker schedules using `ExistingPeriodicWorkPolicy.KEEP` or `UPDATE`.
  - Handle foreground service notifications properly for data sync tasks if needed on Android 14+ (SDK 34+).

## 4. Coroutines & Concurrency Safety

- NEVER call `runBlocking` on the Android Main thread or inside OkHttp interceptors.
- Use `Dispatchers.IO` for disk and network I/O, `Dispatchers.Default` for CPU-intensive tasks (image parsing, sorting), and `Dispatchers.Main.immediate` for UI updates.
- Always handle `CancellationException` by rethrowing it so structured concurrency cancels child jobs cleanly.
- Collect flows in UI using `collectAsStateWithLifecycle()` from `androidx.lifecycle.compose` to prevent collecting in the background when app is stopped.

