package com.example.musicplayer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.musicplayer.presentation.player.PlayerUiState
import com.example.musicplayer.presentation.player.artistLabel

/** Height of the panel content, excluding any system-bar padding. */
private val MiniPlayerHeight = 64.dp

/**
 * Persistent compact playback panel shown above the main screens.
 *
 * The panel is purely presentational: it renders a [PlayerUiState] snapshot and
 * reports taps and transport commands upwards, so the single player owned by
 * `PlaybackService` stays the only playback engine. It is not shown at all while
 * nothing is loaded (see `MiniPlayerHost`).
 *
 * System-bar insets are applied by the host that places this panel, so it can sit
 * directly above the bottom navigation without double padding.
 *
 * @param state Current compact playback state.
 * @param onOpenNowPlaying Invoked when the panel body (not a control) is tapped.
 * @param onTogglePlayPause Toggles play/pause on the service player.
 * @param onNext Skips to the next queue item; only offered when [PlayerUiState.hasNext].
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer),
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .clickable(
                    onClickLabel = "Open Now Playing",
                    onClick = onOpenNowPlaying,
                )
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(
                title = state.title,
                modifier = Modifier.size(42.dp),
                cornerRadius = 8.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = state.errorMessage ?: state.artistLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.isBuffering) {
                Spacer(modifier = Modifier.width(4.dp))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(14.dp)
                        .semantics { contentDescription = "Buffering" },
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(22.dp),
                )
            }

            if (state.hasNext) {
                IconButton(onClick = onNext) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next track",
                        tint = colors.onSurface,
                    )
                }
            }
        }
    }
}