package com.example.viko.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = VPrimary,
    onPrimary = VOnPrimary,
    primaryContainer = VSurfaceVariant,
    onPrimaryContainer = VOnSurface,
    error = VAccentRed,
    onError = Color.White,
    background = VBackground,
    onBackground = VOnSurface,
    surface = VSurface,
    onSurface = VOnSurface,
    surfaceVariant = VSurfaceVariant,
    onSurfaceVariant = VOnSurfaceSecondary,
    outline = VBorder,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = VBackgroundDark,
    error = VAccentRed,
    background = VBackgroundDark,
    onBackground = VOnSurfaceDark,
    surface = VSurfaceDark,
    onSurface = VOnSurfaceDark,
    outline = Color(0xFF444444),
)

@Composable
fun VikoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
