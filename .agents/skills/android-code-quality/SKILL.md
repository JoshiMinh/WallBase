---
name: android-code-quality
description: Best practices for clean architecture, Kotlin idioms, Hilt dependency injection, Room database transactions, and robust error handling in WallBase.
---

# Android Code Quality & Architecture Specialist

This skill outlines code quality, architectural standards, and maintainability rules for WallBase.

## 1. Clean Architecture & Unidirectional Data Flow (UDF)

- **UI Layer**: Composable screens observe state exposed as `StateFlow<UiState>` from ViewModels.
- **ViewModel Layer**: Annotated with `@HiltViewModel`. ViewModels interact ONLY with Repositories; they never directly access DAOs or Retrofit services.
- **Data Layer**: Repositories abstract network sources, scrapers, and Room database DAOs.
- **State Handling**: Model screen states with sealed interfaces or data classes:
  ```kotlin
  sealed interface ScreenUiState {
      data object Loading : ScreenUiState
      data class Success(val items: List<WallpaperItem>) : ScreenUiState
      data class Error(val message: String) : ScreenUiState
  }
  ```

## 2. Dependency Injection (Dagger Hilt)

- Use constructor injection `@Inject constructor(...)` for repositories, managers, and viewmodels.
- Group external dependencies into appropriate Hilt modules:
  - `NetworkModule`: Moshi, OkHttpClient, Retrofit APIs, Scraper clients (`@Singleton`).
  - `DatabaseModule`: `WallBaseDatabase`, `WallpaperDao`, `AlbumDao`, `SourceDao`, `RotationScheduleDao`.
- Avoid service locators or static singletons.

## 3. Room Database & Persistence

- Use suspend functions and `Flow<List<T>>` for asynchronous database queries.
- Mark multi-table mutations or cross-ref updates with `@Transaction`.
- Always provide database migration strategies when changing schemas, or increment database version with explicit migration steps.
- Room entity classes should be pure data classes with explicit primary keys and indices on foreign key columns.

## 4. Error Handling & Network Scrapers

- Wrap network requests and HTML scrapers in safe caller constructs (`Result<T>` or `runCatching`).
- Map network and parsing exceptions to user-friendly error messages or domain failures.
- Always respect rate limits, User-Agent headers, and API authentication requirements for external sources (Reddit, Wallhaven, Unsplash, Pinterest, AlphaCoders, Pixiv).

