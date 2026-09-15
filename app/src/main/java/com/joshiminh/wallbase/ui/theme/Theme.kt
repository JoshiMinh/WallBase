package com.joshiminh.wallbase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1B1918),
    surfaceContainer = Color(0xFF201E1C),
    surfaceContainerHigh = Color(0xFF2A2725),
    surfaceContainerHighest = Color(0xFF35312E),
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF7F2),
    surfaceContainer = Color(0xFFF8F1EC),
    surfaceContainerHigh = Color(0xFFF2EBE6),
    surfaceContainerHighest = Color(0xFFECE5E0),
)

@Composable
fun WallBaseTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appAccentColor: AppAccentColor = AppAccentColor.PINK,
    customAccentColorRgb: String? = null,
    content: @Composable () -> Unit
) {
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
    }

    val baseColorScheme = when {
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val rawAccent = when (appAccentColor) {
        AppAccentColor.PINK -> AccentPink
        AppAccentColor.RED -> AccentRed
        AppAccentColor.BLUE -> AccentBlue
        AppAccentColor.GREEN -> AccentGreen
        AppAccentColor.PURPLE -> AccentPurple
        AppAccentColor.CUSTOM -> {
            // Parse custom color from RGB hex string (e.g., "FF5733")
            parseRgbColor(customAccentColorRgb) ?: AccentPink
        }
    }
    val primaryColor = if (isDark) lerp(rawAccent, Color.White, 0.18f) else rawAccent

    val primaryContainer = lerp(
        baseColorScheme.surfaceVariant,
        primaryColor,
        if (isDark) 0.20f else 0.14f
    )
    val onPrimaryColor = primaryColor.contrastingForeground()
    val onPrimaryContainerColor = primaryContainer.contrastingForeground()

    val colorScheme = baseColorScheme.copy(
        primary = primaryColor,
        onPrimary = onPrimaryColor,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainerColor
    )

    val finalColorScheme = if (appTheme == AppTheme.AMOLED) {
        colorScheme.copy(
            background = AmoledBackground,
            surface = AmoledSurface,
            surfaceVariant = AmoledSurfaceHigh,
            surfaceContainerLowest = AmoledBackground,
            surfaceContainerLow = AmoledSurfaceLow,
            surfaceContainer = AmoledSurfaceLow,
            surfaceContainerHigh = AmoledSurfaceHigh,
            surfaceContainerHighest = Color(0xFF202020),
            outlineVariant = AmoledOutlineVariant,
        )
    } else {
        colorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        content = content
    )
}

private fun parseRgbColor(value: String?): Color? {
    val normalized = value?.trim()?.removePrefix("#") ?: return null
    if (normalized.length != 6) return null
    val rgb = normalized.toLongOrNull(16) ?: return null
    return Color(0xFF000000L or rgb)
}

private fun Color.contrastingForeground(): Color =
    if (luminance() > 0.179f) Color.Black else Color.White
