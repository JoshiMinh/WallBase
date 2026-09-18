# WallBase — AI Agent Engineering Guide

Welcome to **WallBase**! This document is the primary technical manual and rulebook for AI agents working in this repository. All changes, refactorings, and feature implementations MUST adhere to the architecture, design tokens, and standards defined here.

---

## 1. Project Overview & Tech Stack

WallBase is a high-performance, modern Android wallpaper management and discovery application.

- **Language & Runtime**: Kotlin 2.0+ (JVM 21)
- **UI Framework**: 100% Jetpack Compose with Material 3 (Material You)
- **Architecture**: Clean Architecture / MVVM with Unidirectional Data Flow (UDF)
- **Dependency Injection**: Dagger Hilt (`@HiltAndroidApp`, `@HiltViewModel`, `@InstallIn`)
- **Persistence**: Room Database (SQLite) + Jetpack DataStore Preferences
- **Networking**: Retrofit 2 + Moshi (Kotlin codegen) + OkHttp 4 + Jsoup (HTML scraping)
- **Image Pipeline**: Coil 3 for Compose (OkHttp network integration)
- **Navigation**: Jetpack Compose Navigation (`NavHost`, `NavController`)
- **SDK Targets**: `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`

---

## 2. Directory Structure & Key Components

```
WallBase/
├── .agents/                      # AI Agent configuration, rules, and skills
│   ├── AGENTS.md                 # Primary agent guide (this file)
│   └── skills/                   # Workspace-specific agent skills
│       ├── material-you-ui-design/
│       ├── android-performance/
│       └── android-code-quality/
├── .codegraph/                   # CodeGraph symbol index and dependency graph
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml   # Manifest declarations & permissions
│       ├── java/com/joshiminh/wallbase/
│       │   ├── MainActivity.kt               # Single Activity host
│       │   ├── WallBaseApplication.kt        # @HiltAndroidApp application class
│       │   ├── di/                           # Hilt modules (NetworkModule, DatabaseModule)
│       │   ├── data/
│       │   │   ├── WallBaseDatabase.kt       # Room Database definition
│       │   │   ├── dao/                      # DAOs (WallpaperDao, AlbumDao, SourceDao)
│       │   │   ├── entity/                   # Room Entities & Relation models
│       │   │   └── repository/               # Repositories (Wallpaper, Source, Library, Settings)
│       │   ├── navigation/                   # Compose Navigation Routes & TopBar states
│       │   ├── screens/                      # Composable Screen destinations
│       │   │   ├── LandingScreen.kt          # Home / Featured wallpaper feed
│       │   │   ├── BrowseScreen.kt           # Search & filter wallpapers
│       │   │   ├── DetailScreen.kt           # Fullscreen preview, palette, actions
│       │   │   ├── AlbumScreen.kt            # Album contents & management
│       │   │   ├── LibraryScreen.kt          # Favorites, Downloads, Custom Albums
│       │   │   ├── SettingsScreen.kt         # App preferences, appearance, storage
│       │   │   └── SourceScreen.kt           # Source management & custom feeds
│       │   ├── sources/                      # Network scrapers and source providers
│       │   │   ├── Reddit.kt                 # Reddit JSON API with OAuth token manager
│       │   │   ├── Wallhaven.kt              # Wallhaven REST API
│       │   │   ├── Pinterest.kt              # Pinterest feed parser
│       │   │   ├── AlphaCoders.kt            # AlphaCoders scraper
│       │   │   └── Pixiv.kt                  # Pixiv scraper
│       │   └── ui/                           # Reusable UI components & dialogs
│       └── res/                              # Android Resources (Drawables, Mipmaps, Values)
├── icon.png                      # App source logo
├── ROADMAP.md                    # Project development roadmap and progress
└── build.gradle.kts              # Root build configuration
```

---

## 3. UI Design System & Theming Rules

WallBase enforces strict visual consistency and a dedicated brand aesthetic.

### Brand Identity & Palette
- **Fixed Pink Brand Identity**: WallBase uses a fixed pink brand color (`AccentPink = Color(0xFFE91E63)`).
- **No Random Colors**: Do not introduce arbitrary hardcoded `Color(...)` values or one-off accent colors.
- **Material 3 Tokens**: Always use semantic tokens from `MaterialTheme.colorScheme`:
  - `primary`, `onPrimary`, `primaryContainer`, `onPrimaryContainer`
  - `surface`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`
  - `outline`, `outlineVariant`, `background`, `onBackground`

### Frosted Glass Surfaces
- Persistent chrome bars (TopAppBar, BottomNavigation / NavigationRail, floating action pills) use frosted glass styling:
  - Translucent surface fill: `MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)`
  - Subtle low-contrast outline: `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`
  - Restrained elevation (0dp–3dp).
  - **Constraint**: Do NOT add C++ / NDK blur libraries or backdrop-blur dependencies.

### Iconography Rules
- Use Material Icons from a consistent family (`androidx.compose.material.icons`).
- **State Semantics**: Use outlined icons (`Icons.Outlined.*`) when inactive/unselected; use filled icons (`Icons.Filled.*`) when active/selected.
- **Accessibility Requirement**: Every icon-only button (`IconButton`, `Icon`) MUST include a meaningful localized `contentDescription`.

### Spacing & Touch Targets
- Grid spacing follows standard 8dp units (4dp, 8dp, 12dp, 16dp, 24dp).
- Minimum interactive touch target size is **48dp x 48dp** (`Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`).
- System Insets: Apply `WindowInsets.systemBars` or `Scaffold` content padding to ensure edge-to-edge layouts never overlap system bars.

### Motion & Reduced Motion
- Transitions should be snappy (150ms–300ms).
- Always provide a zero-duration reduced-motion path for users with animation restrictions enabled.

---

## 4. Architecture & Coding Conventions

### Presentation (Compose & ViewModel)
- Screens must be stateless where feasible, receiving state data classes and hoisting event lambdas.
- ViewModels expose UI state using `StateFlow<T>` and mutate state internally via `MutableStateFlow<T>`.
- In composables, collect flows using `collectAsStateWithLifecycle()`.
- Use `@Stable` or `@Immutable` annotations on UI state models to enable Compose compiler skipping optimizations.
- Use stable `key(...)` parameters in `LazyVerticalGrid` and `LazyColumn` items.

### Dependency Injection (Dagger Hilt)
- Annotate all ViewModels with `@HiltViewModel` and `@Inject constructor(...)`.
- Group singleton providers into dedicated modules:
  - `NetworkModule`: OkHttpClient, Moshi, Retrofit services, Token managers.
  - `DatabaseModule`: Room database instance and DAOs.
- Do NOT use manual service locators or static singletons.

### Networking & Data Layer
- All network and scraper calls must run on `Dispatchers.IO`.
- **Never use `runBlocking`** on the main thread or inside OkHttp interceptors.
- Handle token management cleanly (e.g. `RedditTokenManager` synchronous execution within interceptors).
- Wrap external network responses in `Result<T>` or sealed domain states to prevent uncaught network exceptions.

### Security & Secrets
- Never commit hardcoded API keys or client secrets.
- Store sensitive keys in `local.properties` and inject them via `BuildConfig` fields in `build.gradle.kts`.

---

## 5. CodeGraph Integration

This repository is indexed by **CodeGraph**.
- A `.codegraph/` directory exists at the root.
- Use `codegraph explore "<query>"` or the MCP tool `codegraph_explore` before manual searching/grepping.
- If files are added or restructured, run `codegraph sync` or `codegraph init` to refresh the index.

---

## 6. Build & Verification Commands

- **Compile Sources**: `./gradlew compileDebugSources`
- **Build Debug APK**: `./gradlew assembleDebug`
- **Run Unit Tests**: `./gradlew testDebugUnitTest`
- **Run Lint Checks**: `./gradlew lintDebug`
- **CodeGraph Status**: `codegraph status`
