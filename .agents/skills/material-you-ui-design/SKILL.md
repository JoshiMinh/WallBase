---
name: material-you-ui-design
description: Guidelines and best practices for Material You (Material 3) UI design, Jetpack Compose layouts, theming tokens, and component styling tailored for WallBase.
---

# Material You & Material 3 UI Design Specialist

This skill defines the Material 3 design system, tokens, and UI guidelines for the WallBase Android application.

## 1. WallBase Brand & Theming Principles

- **Fixed Brand Identity**: WallBase uses a fixed pink brand identity (`AccentPink = Color(0xFFE91E63)`). Do not add arbitrary raw color literals or unvetted accent colors across UI screens.
- **Semantic Theme Tokens**: Always reference `MaterialTheme.colorScheme.*` and `MaterialTheme.typography.*`.
  - Use `primary`, `primaryContainer`, `surface`, `surfaceVariant`, `outline`, `onSurface`, `onPrimary`, etc.
  - Avoid hardcoded `Color(...)` in composables.
- **Dynamic Palette Integration (Detail Screen)**: When viewing a wallpaper, color extraction should derive palette tones smoothly using Android Palette / Monet principles, adapting surface and text contrast while respecting accessibility.

## 2. Chrome Bars & Surfaces (Persistent Chrome)

- TopAppBar and NavigationBar MUST be 100% solid, fully opaque surfaces (`MaterialTheme.colorScheme.surface`, no alpha or transparency).
- Combine with a subtle divider (`MaterialTheme.colorScheme.outlineVariant` with 0.5dp stroke) and 0dp elevation.
- Use clean translate motions (`translationY`) for auto-hiding without alpha fading.
- **Important**: Do NOT introduce blur, backdrop-blur, or translucent alpha fills on chrome bars.

## 3. Iconography Guidelines

- Use Material Icons from a single visual family (`Icons.Outlined` / `Icons.Filled` or `Icons.Rounded`).
- **State Rule**: Outlined icons for default / unselected states; Filled icons for active / selected states.
- **Accessibility Rule**: Every icon-only button (`IconButton`, `Icon`) MUST include a meaningful, localized `contentDescription` (never leave as empty string unless explicitly marked for decorative elements with `null`).

## 4. Spacing, Elevation & Touch Targets

- Follow standard 8dp grid spacing (4dp, 8dp, 12dp, 16dp, 24dp, 32dp).
- **Touch Targets**: All interactive elements must maintain a minimum touch target size of **48dp x 48dp** (`Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` or standard button padding).
- **System Insets**: Properly pad edge-to-edge content using `WindowInsets.systemBars`, `WindowInsets.navigationBars`, and `WindowInsets.statusBars` or `Scaffold` inner padding.

## 5. Motion & Accessibility

- Keep motion short, subtle, and purposeful (150ms–300ms transitions).
- Respect system animation scale and user reduced-motion preferences, providing a 0ms instant fallback path when reduced motion is enabled.
- Ensure minimum contrast ratios: 4.5:1 for normal text, 3:1 for large text and key UI components against their respective backgrounds.

