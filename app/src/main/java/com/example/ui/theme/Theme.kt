package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.example.data.util.ThemeManager
import com.example.data.util.ThemeMode

data class CustomThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val isDark: Boolean
)

val LightCustomColors = CustomThemeColors(
    background = LightCinemaBackground,
    surface = LightCinemaSurface,
    surfaceVariant = LightCinemaSurfaceVariant,
    border = LightCinemaBorder,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    isDark = false
)

val DarkCustomColors = CustomThemeColors(
    background = DarkCinemaBackground,
    surface = DarkCinemaSurface,
    surfaceVariant = DarkCinemaSurfaceVariant,
    border = DarkCinemaBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    isDark = true
)

val LocalAppColors = compositionLocalOf { LightCustomColors }

private val LightColorScheme = lightColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8E0),
    onPrimaryContainer = Color(0xFF3D160D),
    secondary = GoldRating,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFF0D4),
    onSecondaryContainer = Color(0xFF3E2D04),
    tertiary = CyanAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFECE5),
    onTertiaryContainer = Color(0xFF3B1F17),
    background = LightCinemaBackground,
    onBackground = LightTextPrimary,
    surface = LightCinemaSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCinemaSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCinemaBorder
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF45170B),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = GoldRating,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF4A3700),
    onSecondaryContainer = Color(0xFFFFE088),
    tertiary = CyanAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF3E1E16),
    onTertiaryContainer = Color(0xFFFFDBCF),
    background = DarkCinemaBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCinemaSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCinemaSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCinemaBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
    }

    val customColors = if (isDark) DarkCustomColors else LightCustomColors
    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppColors provides customColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
