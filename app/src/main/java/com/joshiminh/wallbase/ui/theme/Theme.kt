package com.joshiminh.wallbase.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor

private val DarkColorScheme = darkColorScheme()

private val LightColorScheme = lightColorScheme()

@Composable
fun WallBaseTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appAccentColor: AppAccentColor = AppAccentColor.PINK,
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

    val primaryColor = when (appAccentColor) {
        AppAccentColor.PINK -> AccentPink
        AppAccentColor.RED -> AccentRed
        AppAccentColor.BLUE -> AccentBlue
        AppAccentColor.GREEN -> AccentGreen
        AppAccentColor.PURPLE -> AccentPurple
    }

    val containerColor = lerp(
        baseColorScheme.surface,
        primaryColor,
        if (isDark) 0.32f else 0.22f
    )

    val colorScheme = baseColorScheme.copy(
        primary = primaryColor,
        secondary = primaryColor,
        tertiary = primaryColor,
        primaryContainer = containerColor,
        secondaryContainer = containerColor,
        tertiaryContainer = containerColor
    )

    val finalColorScheme = if (appTheme == AppTheme.AMOLED) {
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
