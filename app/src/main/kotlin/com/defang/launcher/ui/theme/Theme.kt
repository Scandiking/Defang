package com.defang.launcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnBackgroundDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = SubtleTextDark,
    error = DestructiveRed,
)

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnBackgroundLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnBackgroundLight,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = SubtleTextLight,
    error = DestructiveRed,
)

@Composable
fun DefangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = DefangTypography,
        content = content,
    )
}
