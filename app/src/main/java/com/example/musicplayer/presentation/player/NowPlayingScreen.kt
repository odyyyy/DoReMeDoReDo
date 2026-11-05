package com.example.musicplayer.presentation.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.musicplayer.player.PlaybackRepeatMode
import com.example.musicplayer.presentation.components.ArtworkTile
import com.example.musicplayer.presentation.components.formatDuration
import kotlin.math.abs

/** Length of the linear interpolation between two published positions. */
private const val POSITION_ANIMATION_MS = 500

/**
 * How close a published position must be to the value the user seeked to before
 * the seek bar stops showing the scrubbed position.
 */
private const val SEEK_SETTLE_TOLERANCE_MS = 1_000L

/**
 * Now Playing: the full playback surface.
 *
 * Every control is forwarded to [PlayerViewModel] and therefore to the single
 * player owned by `PlaybackService`; the screen never holds a player of its
 * own. It reads two state streams: [PlayerUiState] for track, transport and
 * mode changes, and [PlaybackProgress] for the seek bar, which is collected
 * inside the seek-bar composable only (via the deferred [NowPlayingContent]
 * `progress` lambda) so position ticks do not recompose the whole screen.
 */
@Composable
fun NowPlayingScreen(
    onBackClicked: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = viewModel.progress.collectAsStateWithLifecycle()

    NowPlayingContent(
        state = state,
        // Deferred read: only the seek bar recomposes on a position tick.
        progress = { progress.value },
        onBackClicked = onBackClicked,
        onOpenQueue = onOpenQueue,
        onTogglePlayPause = viewModel::onTogglePlayPause,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onToggleShuffle = viewModel::onToggleShuffle,
        onCycleRepeatMode = viewModel::onCycleRepeatMode,
        onSeekTo = viewModel::onSeekTo,
        modifier = modifier,
    )
}

/**
 * Stateless Now Playing layout.
 *
 * @param state Track, transport and mode state, or `null` when nothing is
 *   loaded.
 * @param progress Deferred seek-bar state; reading it inside the seek bar keeps
 *   position updates from recomposing the artwork and metadata.
 */
@Composable
fun NowPlayingContent(
    state: PlayerUiState?,
    progress: () -> PlaybackProgress?,
    onBackClicked: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        NowPlayingHeader(onBackClicked = onBackClicked)

        Spacer(modifier = Modifier.height(8.dp))

        if (state == null) {
            EmptyPlayerContent()
            return@Column
        }

        // The artwork takes whatever vertical space is left, so a short window
        // shrinks the image instead of pushing the controls off screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkTile(
                title = state.title,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f),
                cornerRadius = 12.dp,
                textStyle = MaterialTheme.typography.displayMedium,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = state.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = state.artistLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = state.albumLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        SeekBar(
            progress = progress,
            onSeekTo = onSeekTo,
        )

        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        } else if (state.isBuffering) {
            BufferingStatus()
        } else if (state.hasEnded) {
            Text(
                text = "Playback finished",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        TransportControls(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeatMode = onCycleRepeatMode,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onOpenQueue) {
                Text("Queue")
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClicked) {
            Text(
                text = "←",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "Back" },
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Now Playing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Seek bar with elapsed/total time.
 *
 * The published position arrives twice a second, so the thumb is interpolated
 * linearly between two published values while playing; that interpolation is
 * confined to this composable. While the user scrubs — and until the player
 * reports a position close to the seek target — the scrubbed position is shown
 * instead, so the thumb never jumps back after a seek.
 */
@Composable
private fun SeekBar(
    progress: () -> PlaybackProgress?,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = progress()
    val durationMs = current?.durationMs ?: 0L
    val isSeekable = current?.isSeekable == true

    var scrubbedMs by remember(current?.trackUri) { mutableStateOf<Long?>(null) }

    // Drop the scrubbed position once playback reports the seeked position.
    LaunchedEffect(current?.positionMs, current?.trackUri) {
        val target = scrubbedMs ?: return@LaunchedEffect
        val reported = current?.positionMs ?: return@LaunchedEffect
        if (abs(reported - target) <= SEEK_SETTLE_TOLERANCE_MS) scrubbedMs = null
    }

    val targetFraction = when {
        scrubbedMs != null -> seekPositionFraction(scrubbedMs ?: 0L, durationMs)
        current != null -> current.fraction
        else -> 0f
    }
    val isPlaying = current?.isPlaying == true
    val fraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (isPlaying && scrubbedMs == null) {
            tween(durationMillis = POSITION_ANIMATION_MS, easing = LinearEasing)
        } else {
            snap()
        },
        label = "playbackPosition",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { value -> scrubbedMs = positionForFraction(value, durationMs) },
            onValueChangeFinished = { scrubbedMs?.let(onSeekTo) },
            enabled = isSeekable,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Playback position" },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(positionForFraction(fraction, durationMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: PlayerUiState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton(
            glyph = "⇄",
            label = "Shuffle",
            stateDescription = if (state.shuffleModeEnabled) "On" else "Off",
            isActive = state.shuffleModeEnabled,
            onClick = onToggleShuffle,
        )

        IconButton(onClick = onPrevious) {
            Text(
                text = "⏮",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = "Previous track" },
            )
        }

        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = if (state.isPlaying) "❚❚" else "▶",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    contentDescription = if (state.isPlaying) "Pause" else "Play"
                },
            )
        }

        IconButton(
            onClick = onNext,
            enabled = state.hasNext,
        ) {
            Text(
                text = "⏭",
                style = MaterialTheme.typography.titleLarge,
                color = if (state.hasNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                modifier = Modifier.semantics { contentDescription = "Next track" },
            )
        }

        ModeButton(
            glyph = if (state.repeatMode == PlaybackRepeatMode.ONE) "⟲1" else "⟲",
            label = "Repeat",
            stateDescription = when (state.repeatMode) {
                PlaybackRepeatMode.OFF -> "Off"
                PlaybackRepeatMode.ALL -> "All"
                PlaybackRepeatMode.ONE -> "One"
            },
            isActive = state.repeatMode != PlaybackRepeatMode.OFF,
            onClick = onCycleRepeatMode,
        )
    }
}

/** Toggle button whose active state is exposed to accessibility services. */
@Composable
private fun ModeButton(
    glyph: String,
    label: String,
    stateDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = label
            this.stateDescription = stateDescription
        },
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun BufferingStatus(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(14.dp)
                .semantics { contentDescription = "Buffering" },
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Buffering…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyPlayerContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Nothing is playing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Pick a track from the library to start listening.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}