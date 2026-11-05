package com.example.musicplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DoReMeDoReDoColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = OnGold,
    primaryContainer = GoldContainer,
    onPrimaryContainer = GoldSoft,
    secondary = GoldSoft,
    onSecondary = OnGold,
    secondaryContainer = GoldContainerMuted,
    onSecondaryContainer = GoldSoft,
    tertiary = GoldSoft,
    onTertiary = OnGold,
    background = BackgroundBlack,
    onBackground = OnDark,
    surface = SurfaceBlack,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariantBlack,
    onSurfaceVariant = OnDarkMuted,
    surfaceContainerLowest = BackgroundBlack,
    surfaceContainerLow = SurfaceBlack,
    surfaceContainer = SurfaceVariantBlack,
    surfaceContainerHigh = SurfaceElevatedBlack,
    surfaceContainerHighest = SurfaceElevatedBlack,
    outline = OutlineDark,
    outlineVariant = OutlineFaint,
    error = ErrorWarm,
    onError = OnErrorWarm,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    inverseSurface = OnDark,
    inverseOnSurface = BackgroundBlack,
    inversePrimary = GoldContainer,
)

/**
 * Application theme.
 *
 * The product uses a single premium black-and-gold palette rather than following
 * the system light/dark setting or Material You dynamic colour. Gold is reserved
 * for the active element of each screen (current track, active tab, primary
 * action) so the interface stays minimal and calm.
 */
@Composable
fun DoReMeDoReDoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DoReMeDoReDoColorScheme,
        typography = Typography,
        content = content,
    )
}