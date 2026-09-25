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
    primary = AccentPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4E1428),
    onPrimaryContainer = Color(0xFFFFD8E4),
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurface,
    surfaceBright = Color(0xFF353840),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

private val BaseLightColorScheme = lightColorScheme(
    primary = AccentPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E4),
    onPrimaryContainer = Color(0xFF3B071B),
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = Color(0xFFDFE2E7),
    surfaceBright = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightOnBackground,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private fun buildLightColorScheme(accent: AppAccentColor): ColorScheme {
    val primaryColor = when (accent) {
        AppAccentColor.DYNAMIC, AppAccentColor.PINK -> AccentPink
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
        AppAccentColor.DYNAMIC, AppAccentColor.PINK -> AccentPink
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
    dynamicColor: Boolean = false,
    amoledDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK, AppTheme.AMOLED -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val isAmoled = appTheme == AppTheme.AMOLED || (isDark && amoledDark)

    // Dynamic color only applies if explicitly turned on AND using dynamic accent mode
    val useDynamicColor = dynamicColor && (appAccentColor == AppAccentColor.DYNAMIC) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor && !isDark -> dynamicLightColorScheme(context)
        isDark -> buildDarkColorScheme(appAccentColor)
        else -> buildLightColorScheme(appAccentColor)
    }

    val finalColorScheme = if (isAmoled) {
        colorScheme.copy(
            background = Color.Black,
            surface = Color(0xFF0F0F12),
            onSurface = DarkOnSurface,
            surfaceVariant = Color(0xFF17181C),
            onSurfaceVariant = DarkOnSurfaceVariant,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0C),
            surfaceContainer = Color(0xFF121316),
            surfaceContainerHigh = Color(0xFF1B1C20),
            surfaceContainerHighest = Color(0xFF24252A),
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF28292E),
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
