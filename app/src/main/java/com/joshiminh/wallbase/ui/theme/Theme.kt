package com.joshiminh.wallbase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.data.repository.AppTheme

private val DarkColorScheme = darkColorScheme(
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val LightColorScheme = lightColorScheme(
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    background = LightBackground,
    onBackground = LightOnBackground,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

@Composable
fun WallBaseTheme(
    appTheme: AppTheme = AppTheme.LIGHT,
    appAccentColor: AppAccentColor = AppAccentColor.PINK,
    content: @Composable () -> Unit
) {
    val isDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val baseColorScheme = when {
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val primaryColor = when (appAccentColor) {
        AppAccentColor.PINK -> AccentPink
        AppAccentColor.RED -> AccentRed
        AppAccentColor.BLUE -> AccentBlue
        AppAccentColor.GREEN -> AccentGreen
    }

    val containerColor = lerp(
        baseColorScheme.surface,
        primaryColor,
        if (isDark) 0.28f else 0.12f
    )
    val onPrimaryColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White
    val onContainerColor = if (containerColor.luminance() > 0.5f) Color.Black else Color.White

    val colorScheme = baseColorScheme.copy(
        primary = primaryColor,
        secondary = primaryColor,
        tertiary = primaryColor,
        onPrimary = onPrimaryColor,
        onSecondary = onPrimaryColor,
        onTertiary = onPrimaryColor,
        primaryContainer = containerColor,
        secondaryContainer = containerColor,
        tertiaryContainer = containerColor,
        onPrimaryContainer = onContainerColor,
        onSecondaryContainer = onContainerColor,
        onTertiaryContainer = onContainerColor
    )

    val finalColorScheme = if (appTheme == AppTheme.DARK && isDark) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF121212)
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
