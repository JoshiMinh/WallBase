package com.joshiminh.wallbase.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.joshiminh.wallbase.data.repository.AppTheme
import com.joshiminh.wallbase.data.repository.AppAccentColor

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

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
        // Use dynamic color only if the default Pink color is selected
        appAccentColor == AppAccentColor.PINK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val primaryColor = when (appAccentColor) {
        AppAccentColor.PINK -> AccentPink
        AppAccentColor.RED -> AccentRed
        AppAccentColor.BLUE -> AccentBlue
        AppAccentColor.GREEN -> AccentGreen
        AppAccentColor.PURPLE -> AccentPurple
        AppAccentColor.YELLOW -> AccentYellow
    }

    val colorScheme = if (appAccentColor != AppAccentColor.PINK || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        baseColorScheme.copy(
            primary = primaryColor,
            secondary = primaryColor,
            tertiary = primaryColor
        )
    } else {
        baseColorScheme
    }

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