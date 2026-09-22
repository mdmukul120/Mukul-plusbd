package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Light Theme Palette
val LightCinemaBackground = Color(0xFFFFF8F4)       // Clean warm off-white canvas
val LightCinemaSurface = Color(0xFFFFFFFF)          // Crisp white surface
val LightCinemaSurfaceVariant = Color(0xFFF7F1EC)   // Soft warm container surface
val LightCinemaBorder = Color(0xFFEDE3DB)           // Delicate light border
val LightTextPrimary = Color(0xFF1A1A1A)            // Crisp dark primary text
val LightTextSecondary = Color(0xFF4A4A4A)          // Soft dark secondary text
val LightTextMuted = Color(0xFF7A726F)              // Warm muted text

// Dark Theme Palette (Sleek Modern Cinema Dark)
val DarkCinemaBackground = Color(0xFF0F0F14)        // Deep cinema dark canvas
val DarkCinemaSurface = Color(0xFF181820)           // Elevated sleek dark surface
val DarkCinemaSurfaceVariant = Color(0xFF22222E)    // Soft dark container surface
val DarkCinemaBorder = Color(0xFF2C2C3C)            // Subtle dark border
val DarkTextPrimary = Color(0xFFFFFFFF)             // Crisp white text
val DarkTextSecondary = Color(0xFFE2E2EA)           // Soft light secondary text
val DarkTextMuted = Color(0xFF9898A6)               // Muted grey text

// Brand Colors (Consistent in both themes)
val BrandRed = Color(0xFFFA4D28)                    // Unified brand primary
val BrandRedLight = Color(0xFFFF6D47)               // Warm orange-red light
val BrandRedDark = Color(0xFFD4310E)                // Deep fiery red-orange
val GoldRating = Color(0xFFFF9800)                  // Warm Amber Rating
val CyanAccent = Color(0xFFFA4D28)                  // Unified brand accent
val GreenSuccess = Color(0xFF10B981)

// Auth Colors
val AuthBrandPrimary = BrandRed
val AuthBrandGradientStart = Color(0xFFFF5E36)
val AuthBrandGradientEnd = Color(0xFFE53915)
val AuthInputBorder = BrandRed

// Dynamically themed getters using LocalAppColors
val CinemaBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.background

val CinemaSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surface

val CinemaSurfaceVariant: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surfaceVariant

val CinemaBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.border

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textMuted

val AuthBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.background

val AuthSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surface

val AuthTextDark: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textPrimary

val AuthTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.textMuted

val AuthSocialBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.border

val AuthInputBg: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current.surfaceVariant
