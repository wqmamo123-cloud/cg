package com.example.chess.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark colour scheme — the primary mode for the Chess App.
 *
 * Deep navy surfaces with rich gold accents create a premium, focused
 * environment that lets the chess board stand out as the centrepiece.
 * High contrast between surface and on-surface colours ensures readability
 * even in low-light conditions (common during tournament play).
 */
private val DarkColorScheme = darkColorScheme(
    primary = Gold500,
    onPrimary = Navy900,
    primaryContainer = Gold700,
    onPrimaryContainer = Gold100,

    secondary = BoardLightSquare,
    onSecondary = Navy900,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = TextPrimary,

    tertiary = AccentBlue,
    onTertiary = Navy900,
    tertiaryContainer = Color(0xFF1A3A5C),
    onTertiaryContainer = Color(0xFFB0D4F1),

    background = Navy900,
    onBackground = TextPrimary,

    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,

    outline = DividerColor,
    outlineVariant = Navy600,

    error = AccentRed,
    onError = Color.White,
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFB4AB),

    inverseSurface = TextPrimary,
    inverseOnSurface = Navy900,
    inversePrimary = Gold600,

    surfaceTint = Gold500,
    scrim = Color.Black
)

/**
 * Light colour scheme — for users who prefer a bright interface.
 *
 * Maintains the same gold accent but uses warm cream and soft grey
 * surfaces instead of dark navy. The board colours remain consistent
 * across both themes.
 */
private val LightColorScheme = lightColorScheme(
    primary = Gold600,
    onPrimary = Color.White,
    primaryContainer = Gold100,
    onPrimaryContainer = Gold700,

    secondary = Color(0xFF6D5D3E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E1BB),
    onSecondaryContainer = Color(0xFF241A04),

    tertiary = Color(0xFF4A6178),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCE5FF),
    onTertiaryContainer = Color(0xFF001E30),

    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF1C1B1A),

    surface = Color(0xFFFFF8F0),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFF0E6D6),
    onSurfaceVariant = Color(0xFF504539),

    outline = Color(0xFF827568),
    outlineVariant = Color(0xFFD4C4B0),

    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    inverseSurface = Color(0xFF2F2E2D),
    inverseOnSurface = Color(0xFFF1EFE9),
    inversePrimary = Gold400,

    surfaceTint = Gold600,
    scrim = Color.Black
)

/**
 * Top-level theme composable for the Chess Application.
 *
 * Wraps the entire app's content in a Material3 theme with the
 * premium colour scheme. Also configures the system status bar
 * and navigation bar to match the app's colour scheme, ensuring
 * a seamless edge-to-edge appearance.
 *
 * Usage:
 * ```
 * setContent {
 *     ChessTheme {
 *         // App content
 *     }
 * }
 * ```
 *
 * @param darkTheme Whether to use the dark colour scheme.
 *                  Defaults to the system's dark theme setting.
 * @param content   The composable content to be themed.
 */
@Composable
fun ChessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Configure the system bars to match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()

            // Light status bar icons for the dark background
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
