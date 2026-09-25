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
import androidx.compose.ui.platform.LocalContext
import com.joshiminh.wallbase.data.repository.AppAccentColor
import com.joshiminh.wallbase.data.repository.AppTheme

private data class AccentPalette(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

private fun getDarkAccentPalette(accent: AppAccentColor): AccentPalette {
    return when (accent) {
        AppAccentColor.DYNAMIC, AppAccentColor.PINK -> AccentPalette(
            primary = AccentPinkLight,
            onPrimary = Color(0xFF3C0018),
            primaryContainer = AccentPinkContainerDark,
            onPrimaryContainer = Color(0xFFFFD9E2),
        )
        AppAccentColor.BLUE -> AccentPalette(
            primary = AccentBlueLight,
            onPrimary = Color(0xFF003258),
            primaryContainer = AccentBlueContainerDark,
            onPrimaryContainer = Color(0xFFD6E4FF),
        )
        AppAccentColor.RED -> AccentPalette(
            primary = AccentRedLight,
            onPrimary = Color(0xFF410002),
            primaryContainer = AccentRedContainerDark,
            onPrimaryContainer = Color(0xFFFFDAD6),
        )
        AppAccentColor.GREEN -> AccentPalette(
            primary = AccentGreenLight,
            onPrimary = Color(0xFF00390A),
            primaryContainer = AccentGreenContainerDark,
            onPrimaryContainer = Color(0xFFC8E6C9),
        )
    }
}

private fun getLightAccentPalette(accent: AppAccentColor): AccentPalette {
    return when (accent) {
        AppAccentColor.DYNAMIC, AppAccentColor.PINK -> AccentPalette(
            primary = AccentPinkDark,
            onPrimary = Color.White,
            primaryContainer = AccentPinkContainerLight,
            onPrimaryContainer = Color(0xFF3E001D),
        )
        AppAccentColor.BLUE -> AccentPalette(
            primary = AccentBlueDark,
            onPrimary = Color.White,
            primaryContainer = AccentBlueContainerLight,
            onPrimaryContainer = Color(0xFF001B3E),
        )
        AppAccentColor.RED -> AccentPalette(
            primary = AccentRedDark,
            onPrimary = Color.White,
            primaryContainer = AccentRedContainerLight,
            onPrimaryContainer = Color(0xFF410002),
        )
        AppAccentColor.GREEN -> AccentPalette(
            primary = AccentGreenDark,
            onPrimary = Color.White,
            primaryContainer = AccentGreenContainerLight,
            onPrimaryContainer = Color(0xFF002204),
        )
    }
}

private fun buildDarkColorScheme(accent: AppAccentColor): ColorScheme {
    val palette = getDarkAccentPalette(accent)
    return darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.primary,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.primaryContainer,
        onSecondaryContainer = palette.onPrimaryContainer,
        tertiary = palette.primary,
        onTertiary = palette.onPrimary,
        tertiaryContainer = palette.primaryContainer,
        onTertiaryContainer = palette.onPrimaryContainer,
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
        surfaceBright = Color(0xFF333640),
        background = DarkBackground,
        onBackground = DarkOnBackground,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = LightSurface,
        inverseOnSurface = LightOnSurface,
        inversePrimary = AccentPinkDark,
    )
}

private fun buildAmoledColorScheme(accent: AppAccentColor): ColorScheme {
    val palette = getDarkAccentPalette(accent)
    return darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.primary,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.primaryContainer,
        onSecondaryContainer = palette.onPrimaryContainer,
        tertiary = palette.primary,
        onTertiary = palette.onPrimary,
        tertiaryContainer = palette.primaryContainer,
        onTertiaryContainer = palette.onPrimaryContainer,
        surface = AmoledSurface,
        onSurface = AmoledOnSurface,
        surfaceVariant = AmoledSurfaceVariant,
        onSurfaceVariant = AmoledOnSurfaceVariant,
        surfaceContainerLowest = AmoledSurfaceContainerLowest,
        surfaceContainerLow = AmoledSurfaceContainerLow,
        surfaceContainer = AmoledSurfaceContainer,
        surfaceContainerHigh = AmoledSurfaceContainerHigh,
        surfaceContainerHighest = AmoledSurfaceContainerHighest,
        surfaceDim = AmoledBackground,
        surfaceBright = Color(0xFF26272D),
        background = AmoledBackground,
        onBackground = AmoledOnBackground,
        outline = AmoledOutline,
        outlineVariant = AmoledOutlineVariant,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = LightSurface,
        inverseOnSurface = LightOnSurface,
        inversePrimary = AccentPinkDark,
    )
}

private fun buildLightColorScheme(accent: AppAccentColor): ColorScheme {
    val palette = getLightAccentPalette(accent)
    return lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primaryContainer,
        onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.primary,
        onSecondary = palette.onPrimary,
        secondaryContainer = palette.primaryContainer,
        onSecondaryContainer = palette.onPrimaryContainer,
        tertiary = palette.primary,
        onTertiary = palette.onPrimary,
        tertiaryContainer = palette.primaryContainer,
        onTertiaryContainer = palette.onPrimaryContainer,
        surface = LightSurface,
        onSurface = LightOnSurface,
        surfaceVariant = LightSurfaceVariant,
        onSurfaceVariant = LightOnSurfaceVariant,
        surfaceContainerLowest = LightSurfaceContainerLowest,
        surfaceContainerLow = LightSurfaceContainerLow,
        surfaceContainer = LightSurfaceContainer,
        surfaceContainerHigh = LightSurfaceContainerHigh,
        surfaceContainerHighest = LightSurfaceContainerHighest,
        surfaceDim = Color(0xFFDCE0E7),
        surfaceBright = Color(0xFFFFFFFF),
        background = LightBackground,
        onBackground = LightOnBackground,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        inverseSurface = DarkSurface,
        inverseOnSurface = DarkOnSurface,
        inversePrimary = AccentPinkLight,
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

    // Dynamic color (Material You Monet) strictly applies ONLY when enabled AND dynamic accent mode is selected
    val useDynamicColor = dynamicColor && (appAccentColor == AppAccentColor.DYNAMIC) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColor && isDark -> dynamicDarkColorScheme(context)
        useDynamicColor && !isDark -> dynamicLightColorScheme(context)
        isAmoled -> buildAmoledColorScheme(appAccentColor)
        isDark -> buildDarkColorScheme(appAccentColor)
        else -> buildLightColorScheme(appAccentColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
