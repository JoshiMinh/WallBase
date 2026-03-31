# WallBase Roadmap

This document outlines the planned ideas and improvements to implement for WallBase, aiming to enhance the overall experience, usability, and UI aesthetics.

## Proposed Features

### 1. Advanced Tagging & Smart Filtering
- **Description**: Introduce a robust capability for tagging individual wallpapers (e.g., "dark mode", "landscapes", "abstract").
- **Goal**: Make a massive local library easier to navigate through filters, distinct from regular albums.

### 2. Android Home Screen Widget
- **Description**: Add a customizable Android widget for the home screen.
- **Goal**: Automatically rotate and display wallpapers from a user's library or specific album, directly on their Android home screen, increasing engagement.

### 3. UI/UX Polish and Animations
- **Description**: Enrich the current Jetpack Compose UI with top-tier micro-animations.
- **Goal**: Include features like parallax scrolling effects, refined bouncy overscroll behavior, and smoother shared-element transitions between grid items and the detail screen.

### 4. Deep Material You (Dynamic Color) Integration
- **Description**: Extract color palettes from the currently viewed/selected wallpaper inside the app.
- **Goal**: Offer a deeply immersive experience by letting the app theme dynamically reflect the image currently on-screen, not just strictly following system-wide wallpaper colors.

### 5. Architectural Improvements [COMPLETED]
- **Description**: Break down huge Compose files (like `LibraryScreen.kt` and `AlbumDetailScreen.kt`) into smaller, modular components.
- **Goal**: Improve project maintainability and Compose compiler performance (skippability/restartability).

---
*Roadmap continuously updated based on community feedback and core development goals.*
