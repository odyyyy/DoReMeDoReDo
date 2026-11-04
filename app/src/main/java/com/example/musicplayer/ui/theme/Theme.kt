package com.example.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MusicPlayerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = BackgroundDark,
    secondary = Accent,
    onSecondary = BackgroundDark,
    tertiary = Accent,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnBackgroundDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

/**
 * Application theme.
 *
 * The product uses a single dark, near-black palette rather than following the
 * system light/dark setting or Material You dynamic colour. This keeps the
 * visual direction minimal and predictable.
 */
@Composable
fun MusicPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusicPlayerColorScheme,
        typography = Typography,
        content = content,
    )
}
