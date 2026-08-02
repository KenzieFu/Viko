package com.example.viko.view.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Viko is dark-only, so there is one scheme and it ignores the system setting.
// Screens mostly reference the V* colors directly, but Material3's own widgets
// (Button, ModalBottomSheet scrim, TextField, ripples) read this scheme — so it
// has to be filled in properly or those widgets come back light.
private val VikoDarkColorScheme = darkColorScheme(
    primary = VAccentBlue,
    onPrimary = Color.White,
    primaryContainer = VActivePanel,
    onPrimaryContainer = Color.White,
    secondary = VOnSurfaceSecondary,
    onSecondary = VBackground,
    error = VAccentRed,
    onError = Color.White,
    background = VBackground,
    onBackground = VOnSurface,
    surface = VSurface,
    onSurface = VOnSurface,
    surfaceVariant = VSurfaceVariant,
    onSurfaceVariant = VOnSurfaceSecondary,
    surfaceContainer = VSurface,       // ModalBottomSheet body
    surfaceContainerHigh = VSurfaceVariant,
    outline = VBorder,
    outlineVariant = VBorder,
    scrim = Color(0xCC000000),
)

@Composable
fun VikoTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // false = light (white) status/navigation bar icons, for a dark background
            val insets = WindowCompat.getInsetsController(window, view)
            insets.isAppearanceLightStatusBars = false
            insets.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = VikoDarkColorScheme,
        typography = Typography,
        content = content
    )
}
