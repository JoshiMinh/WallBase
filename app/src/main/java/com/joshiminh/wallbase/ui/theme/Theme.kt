package com.joshiminh.wallbase.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.data.repository.AppTheme

private val BaseDarkColorScheme = darkColorScheme(
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val BaseLightColorScheme = lightColorScheme(
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    background = LightBackground,
    onBackground = LightOnBackground,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private fun buildLightColorScheme(accent: AppAccentColor): ColorScheme {
    val primaryColor = when (accent) {
        AppAccentColor.PINK -> AccentPink
        AppAccentColor.RED -> AccentRed
        AppAccentColor.BLUE -> AccentBlue
        AppAccentColor.GREEN -> AccentGreen
    }
    val containerColor = lerp(LightSurface, primaryColor, 0.12f)
    val onPrimaryColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White
    val onContainerColor = if (containerColor.luminance() > 0.5f) Color.Black else Color.White

    return BaseLightColorScheme.copy(
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
        onTertiaryContainer = onContainerColor,
    )
}

private fun buildDarkColorScheme(accent: AppAccentColor): ColorScheme {
    val primaryColor = when (accent) {
        AppAccentColor.PINK -> AccentPink
        AppAccentColor.RED -> AccentRed
        AppAccentColor.BLUE -> AccentBlue
        AppAccentColor.GREEN -> AccentGreen
    }
    val containerColor = lerp(DarkSurface, primaryColor, 0.28f)
    val onPrimaryColor = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White
    val onContainerColor = if (containerColor.luminance() > 0.5f) Color.Black else Color.White

    return BaseDarkColorScheme.copy(
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
        onTertiaryContainer = onContainerColor,
    )
}

val Shapes = Shapes(
    small = WallBaseShapes.control,
    medium = WallBaseShapes.card,
    large = WallBaseShapes.dialog,
    extraLarge = WallBaseShapes.featured,
)

@Composable
fun WallBaseTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appAccentColor: AppAccentColor = AppAccentColor.PINK,
    dynamicColor: Boolean = true,
    amoledDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val useDynamicColor = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor && !isDark -> dynamicLightColorScheme(context)
        isDark -> buildDarkColorScheme(appAccentColor)
        else -> buildLightColorScheme(appAccentColor)
    }

    val finalColorScheme = if (isDark && amoledDark) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF141414),
            surfaceContainer = Color(0xFF181818),
            surfaceContainerHigh = Color(0xFF222222),
            surfaceContainerHighest = Color(0xFF2C2C2C),
        )
    } else {
        colorScheme
    }

    MaterialTheme(
        colorScheme = finalColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
