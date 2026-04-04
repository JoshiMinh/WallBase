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

private val DarkColorScheme = darkColorScheme()

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
    background = LightBackground,
    onBackground = LightOnBackground
)

@Composable
fun WallBaseTheme(
    appTheme: AppTheme = AppTheme.LIGHT,
    appAccentColor: AppAccentColor = AppAccentColor.PINK,
    customAccentColorRgb: String? = null,
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
        AppAccentColor.PURPLE -> AccentPurple
        AppAccentColor.CUSTOM -> {
            // Parse custom color from RGB hex string (e.g., "FF5733")
            customAccentColorRgb?.let {
                try {
                    Color(0xFF000000 or it.toLong(16))
                } catch (e: Exception) {
                    AccentPink // Fallback to default if parsing fails
                }
            } ?: AccentPink
        }
    }

    val containerColor = lerp(
        baseColorScheme.surface,
        primaryColor,
        if (isDark) 0.32f else 0.22f
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
