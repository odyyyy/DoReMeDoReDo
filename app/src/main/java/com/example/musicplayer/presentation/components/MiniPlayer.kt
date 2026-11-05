package com.example.musicplayer.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
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

/** Height of the panel content, excluding the system navigation bar inset. */
private val MiniPlayerHeight = 64.dp

/**
 * Persistent compact playback panel shown above the main screens.
 *
 * The panel is purely presentational: it renders a [PlayerUiState] snapshot and
 * reports taps and transport commands upwards, so the single player owned by
 * `PlaybackService` stays the only playback engine. It is not shown at all
 * while nothing is loaded (see `MiniPlayerHost`).
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // The Scaffold bottom bar is not inset-aware, so the panel lifts its
            // own content above the navigation bar and keeps drawing behind it.
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MiniPlayerHeight)
                .clickable(
                    onClickLabel = "Open Now Playing",
                    onClick = onOpenNowPlaying,
                )
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkTile(
                title = state.title,
                modifier = Modifier.size(44.dp),
                cornerRadius = 6.dp,
                textStyle = MaterialTheme.typography.titleMedium,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = state.errorMessage ?: state.artistLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (state.isBuffering) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(16.dp)
                        .semantics { contentDescription = "Buffering" },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onTogglePlayPause) {
                Text(
                    text = if (state.isPlaying) "❚❚" else "▶",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics {
                        contentDescription = if (state.isPlaying) "Pause" else "Play"
                    },
                )
            }

            if (state.hasNext) {
                IconButton(onClick = onNext) {
                    Text(
                        text = "⏭",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { contentDescription = "Next track" },
                    )
                }
            }
        }
    }
}